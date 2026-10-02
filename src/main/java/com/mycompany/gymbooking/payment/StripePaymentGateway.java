package com.mycompany.gymbooking.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.exception.PaymentProviderException;
import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Stripe gateway using the REST API directly (no SDK). Stripe expects form-encoded request bodies;
 * write calls send an Idempotency-Key so retries never create a second charge or refund.
 * Only test-mode keys are accepted so the project can never move real money.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final RestClient stripe;
    private final ObjectMapper objectMapper;
    private final String publishableKey;
    private final boolean configured;

    public StripePaymentGateway(RestClient.Builder builder,
                                ObjectMapper objectMapper,
                                @Value("${app.payments.stripe.secret-key:}") String secretKey,
                                @Value("${app.payments.stripe.publishable-key:}") String publishableKey,
                                @Value("${app.payments.stripe.api-base}") String apiBase) {
        secretKey = secretKey.trim();
        publishableKey = publishableKey.trim();
        refuseLiveKey(secretKey);
        refuseLiveKey(publishableKey);

        this.objectMapper = objectMapper;
        this.publishableKey = publishableKey;
        this.configured = (secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_"))
                && publishableKey.startsWith("pk_test_");
        if (!configured) {
            log.warn("Payments are OFF: add app.payments.stripe.secret-key (sk_test_...) and "
                    + "app.payments.stripe.publishable-key (pk_test_...) to local.properties, then restart.");
        }

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.stripe = builder
                .baseUrl(apiBase)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toException(response);
                })
                .build();
    }

    @Override
    public String provider() {
        return "stripe";
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }

    @Override
    public GatewayPayment createPayment(PaymentOrder order) {
        requireConfigured();

        // Stripe uses bracket notation for arrays and maps in form bodies.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(CurrencyUnits.toMinorUnits(order.amount(), order.currency())));
        form.add("currency", order.currency().toLowerCase(Locale.ROOT));
        form.add("payment_method_types[]", "card");
        form.add("description", order.description());
        order.metadata().forEach((key, value) -> form.add("metadata[" + key + "]", value));

        JsonNode paymentIntent = call(() -> stripe.post()
                .uri("/v1/payment_intents")
                .header("Idempotency-Key", order.idempotencyKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        return toGatewayPayment(paymentIntent);
    }

    @Override
    public GatewayPayment getPayment(String providerPaymentId) {
        requireConfigured();
        // Expand latest_charge to get the card brand and last 4 digits in the same response.
        JsonNode paymentIntent = call(() -> stripe.get()
                .uri(uri -> uri.path("/v1/payment_intents/{id}")
                        .queryParam("expand[]", "latest_charge")
                        .build(providerPaymentId))
                .retrieve()
                .body(JsonNode.class));
        return toGatewayPayment(paymentIntent);
    }

    @Override
    public GatewayRefund refund(String providerPaymentId, String idempotencyKey) {
        requireConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", providerPaymentId);    // no amount -> full refund
        form.add("reason", "requested_by_customer");

        JsonNode refund = call(() -> stripe.post()
                .uri("/v1/refunds")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        String status = refund.path("status").asText();
        if (status.equals("failed") || status.equals("canceled")) {
            throw new PaymentProviderException("Stripe couldn't refund this payment (refund status: " + status + ").");
        }
        return new GatewayRefund(refund.path("id").asText(), status);
    }

    private GatewayPayment toGatewayPayment(JsonNode paymentIntent) {
        String currency = paymentIntent.path("currency").asText().toUpperCase(Locale.ROOT);
        JsonNode card = paymentIntent.path("latest_charge").path("payment_method_details").path("card");
        return new GatewayPayment(
                paymentIntent.path("id").asText(),
                textOrNull(paymentIntent, "client_secret"),
                toStatus(paymentIntent.path("status").asText()),
                CurrencyUnits.fromMinorUnits(paymentIntent.path("amount").asLong(), currency),
                currency,
                textOrNull(card, "brand"),
                textOrNull(card, "last4"));
    }

    private static GatewayPaymentStatus toStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> GatewayPaymentStatus.SUCCEEDED;
            case "processing", "requires_capture" -> GatewayPaymentStatus.PROCESSING;
            case "canceled" -> GatewayPaymentStatus.CANCELED;
            // requires_payment_method, requires_confirmation, requires_action
            default -> GatewayPaymentStatus.WAITING_FOR_CUSTOMER;
        };
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            log.error("Could not reach Stripe: {}", e.getMessage());
            throw new PaymentProviderException(
                    "The payment service can't be reached right now. Please try again in a few minutes.");
        }
    }

    /** Stripe masks API keys in error messages, so logging the message is safe. */
    private PaymentProviderException toException(ClientHttpResponse response) throws IOException {
        String message;
        try {
            message = objectMapper.readTree(response.getBody()).path("error").path("message").asText("");
        } catch (IOException e) {
            message = "";
        }
        if (message.isBlank()) {
            message = "HTTP " + response.getStatusCode().value();
        }
        log.error("Stripe refused a request: {}", message);
        return new PaymentProviderException("Stripe: " + message);
    }

    private void requireConfigured() {
        if (!configured) {
            throw new ServiceUnavailableException("PAYMENTS_NOT_CONFIGURED",
                    "Payments aren't set up yet: the Stripe test keys are missing on the server.");
        }
    }

    private static void refuseLiveKey(String key) {
        if (key.startsWith("sk_live_") || key.startsWith("pk_live_") || key.startsWith("rk_live_")) {
            throw new IllegalStateException(
                    "A LIVE Stripe key is in local.properties. This project only runs with TEST keys (sk_test_ / pk_test_).");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
