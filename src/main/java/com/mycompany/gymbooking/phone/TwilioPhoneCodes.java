package com.mycompany.gymbooking.phone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import com.mycompany.gymbooking.exception.TooManyRequestsException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Texts codes with Twilio Verify, which creates each code, sends it and checks it. Used when
 * app.sms.twilio.account-sid is set. Like the Stripe client, it calls the REST API directly:
 * two endpoints don't need an SDK.
 */
@Component
@ConditionalOnExpression("'${app.notifications.mode:console}' != 'test' and '${app.sms.twilio.account-sid:}' != ''")
public class TwilioPhoneCodes implements PhoneCodes {

    private static final Logger log = LoggerFactory.getLogger(TwilioPhoneCodes.class);

    /** Twilio's answer: the HTTP status and the JSON body. */
    private record Reply(int status, JsonNode body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private final RestClient twilio;
    private final ObjectMapper objectMapper;

    public TwilioPhoneCodes(RestClient.Builder builder,
                            ObjectMapper objectMapper,
                            @Value("${app.sms.twilio.account-sid}") String accountSid,
                            @Value("${app.sms.twilio.auth-token:}") String authToken,
                            @Value("${app.sms.twilio.verify-service-sid:}") String serviceSid,
                            @Value("${app.sms.twilio.api-base}") String apiBase) {
        // Fail at startup rather than on the first code.
        if (authToken.isBlank() || serviceSid.isBlank()) {
            throw new IllegalStateException("Twilio needs app.sms.twilio.account-sid, app.sms.twilio.auth-token "
                    + "and app.sms.twilio.verify-service-sid in local.properties.");
        }
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        String credentials = accountSid.trim() + ":" + authToken.trim();

        this.twilio = builder
                .baseUrl(apiBase + "/v2/Services/" + serviceSid.trim())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    public void send(String phone) {
        Reply reply = post("/Verifications", form("To", phone, "Channel", "sms"));
        if (reply.ok()) {
            return;
        }
        log.warn("Twilio didn't send a code to {}: {}", phone, describe(reply));
        if (reply.status() == 429) {
            throw new TooManyRequestsException("TOO_MANY_CODES",
                    "Too many codes were sent to this number. Please try again in 10 minutes.", 600);
        }
        if (reply.status() == 400) {   // e.g. not a mobile number, or a country Twilio can't text
            throw new BadRequestException("PHONE_CANT_RECEIVE_SMS",
                    "We can't text this number. Check it, or change it in Profile.");
        }
        throw unavailable();
    }

    @Override
    public Check check(String phone, String code) {
        Reply reply = post("/VerificationCheck", form("To", phone, "Code", code));
        if (reply.ok()) {
            return switch (reply.body().path("status").asText()) {
                case "approved" -> Check.CORRECT;
                case "pending" -> Check.WRONG;
                default -> Check.EXPIRED;   // canceled, expired, max_attempts_reached, ...
            };
        }
        // 404: no code waiting any more (expired or already used); 429: too many tries
        if (reply.status() == 404 || reply.status() == 429) {
            return Check.EXPIRED;
        }
        log.warn("Twilio couldn't check a code for {}: {}", phone, describe(reply));
        throw unavailable();
    }

    private Reply post(String path, MultiValueMap<String, String> form) {
        try {
            return twilio.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> new Reply(response.getStatusCode().value(), readJson(response.getBody())));
        } catch (RestClientException e) {   // couldn't connect, or no answer in time
            log.error("Could not reach Twilio: {}", e.getMessage());
            throw unavailable();
        }
    }

    private JsonNode readJson(InputStream body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static MultiValueMap<String, String> form(String... namesAndValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            form.add(namesAndValues[i], namesAndValues[i + 1]);
        }
        return form;
    }

    /** E.g. "HTTP 400, Twilio error 60200: Invalid parameter `To`". */
    private static String describe(Reply reply) {
        return "HTTP " + reply.status() + ", Twilio error " + reply.body().path("code").asText("?")
                + ": " + reply.body().path("message").asText("");
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("SMS_UNAVAILABLE",
                "Text messages can't be sent right now. Please try again in a few minutes.");
    }
}
