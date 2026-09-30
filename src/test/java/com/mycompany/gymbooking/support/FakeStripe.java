package com.mycompany.gymbooking.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Local HTTP server that imitates the Stripe endpoints StripePaymentGateway uses (create and
 * retrieve PaymentIntent, create refund), including Idempotency-Key replay. Tests drive payment
 * outcomes with pay, decline, processing and failNextRefund.
 */
public final class FakeStripe implements AutoCloseable {

    public static final String SECRET_KEY = "sk_test_fake_key_for_tests";

    public record Request(String method, String path, Map<String, String> form, String idempotencyKey, String contentType) {
    }

    private record Saved(String fingerprint, int status, String body) {
    }

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Map<String, Object>> intents = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> refunds = new LinkedHashMap<>();
    private final Map<String, Saved> idempotency = new LinkedHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private int nextId = 1;
    private int refundFailuresToSimulate = 0;

    private FakeStripe() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    public static FakeStripe start() throws IOException {
        return new FakeStripe();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public synchronized void pay(String paymentIntentId, String brand, String last4) {
        Map<String, Object> intent = intent(paymentIntentId);
        intent.put("status", "succeeded");
        intent.put("_charge", Map.of(
                "id", "ch_" + nextId++,
                "object", "charge",
                "payment_method_details", Map.of("type", "card", "card", Map.of("brand", brand, "last4", last4))));
    }

    /** A declined card leaves the intent open (requires_payment_method) so another card can be tried. */
    public synchronized void decline(String paymentIntentId) {
        intent(paymentIntentId).put("status", "requires_payment_method");
    }

    public synchronized void processing(String paymentIntentId) {
        intent(paymentIntentId).put("status", "processing");
    }

    /** Makes the next refund request fail with a 500. */
    public synchronized void failNextRefund() {
        refundFailuresToSimulate++;
    }

    public List<Request> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    public synchronized int paymentIntentCount() {
        return intents.size();
    }

    public synchronized List<Map<String, Object>> refundsFor(String paymentIntentId) {
        return refunds.values().stream().filter(r -> paymentIntentId.equals(r.get("payment_intent"))).toList();
    }

    public synchronized Map<String, Object> intent(String paymentIntentId) {
        Map<String, Object> intent = intents.get(paymentIntentId);
        if (intent == null) {
            throw new IllegalArgumentException("No payment intent " + paymentIntentId);
        }
        return intent;
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Map<String, String> form = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Map<String, String> query = parse(exchange.getRequestURI().getRawQuery());
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        requests.add(new Request(method, path, form, key, exchange.getRequestHeaders().getFirst("Content-Type")));

        if (!("Bearer " + SECRET_KEY).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            send(exchange, 401, error("Invalid API Key provided: sk_test_****"));
            return;
        }

        // Like Stripe, replay the stored response for a reused Idempotency-Key
        if (key != null && idempotency.containsKey(key)) {
            Saved saved = idempotency.get(key);
            if (!saved.fingerprint().equals(path + form)) {
                send(exchange, 400, error("Keys for idempotent requests can only be used with the same parameters"));
            } else {
                send(exchange, saved.status(), saved.body());
            }
            return;
        }

        int status;
        String body;
        if (method.equals("POST") && path.equals("/v1/payment_intents")) {
            status = 200;
            body = json.writeValueAsString(createIntent(form));
        } else if (method.equals("GET") && path.startsWith("/v1/payment_intents/")) {
            String id = path.substring("/v1/payment_intents/".length());
            Map<String, Object> intent = intents.get(id);
            status = intent == null ? 404 : 200;
            body = intent == null ? error("No such payment_intent: '" + id + "'")
                    : json.writeValueAsString(view(intent, "latest_charge".equals(query.get("expand[]"))));
        } else if (method.equals("POST") && path.equals("/v1/refunds")) {
            Object[] answer = refund(form);
            status = (int) answer[0];
            body = (String) answer[1];
        } else {
            status = 404;
            body = error("Unrecognized request URL");
        }

        if (key != null) {
            idempotency.put(key, new Saved(path + form, status, body));
        }
        send(exchange, status, body);
    }

    private Map<String, Object> createIntent(Map<String, String> form) {
        String id = "pi_test" + nextId++;
        Map<String, String> metadata = new LinkedHashMap<>();
        form.forEach((k, v) -> {
            if (k.startsWith("metadata[")) {
                metadata.put(k.substring(9, k.length() - 1), v);
            }
        });
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("id", id);
        intent.put("object", "payment_intent");
        intent.put("amount", Long.parseLong(form.get("amount")));
        intent.put("currency", form.get("currency"));
        intent.put("client_secret", id + "_secret_test");
        intent.put("status", "requires_payment_method");
        intent.put("description", form.get("description"));
        intent.put("metadata", metadata);
        intents.put(id, intent);
        return view(intent, false);
    }

    private Object[] refund(Map<String, String> form) throws IOException {
        Map<String, Object> intent = intents.get(form.get("payment_intent"));
        if (intent == null) {
            return new Object[]{404, error("No such payment_intent")};
        }
        if (refundFailuresToSimulate > 0) {
            refundFailuresToSimulate--;
            return new Object[]{500, error("An unknown error occurred (simulated)")};
        }
        if (!"succeeded".equals(intent.get("status"))) {
            return new Object[]{400, error("This PaymentIntent does not have a successful charge to refund.")};
        }
        if (Boolean.TRUE.equals(intent.get("_refunded"))) {
            return new Object[]{400, error("Charge has already been refunded.")};
        }
        intent.put("_refunded", true);
        String id = "re_test" + nextId++;
        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("id", id);
        refund.put("object", "refund");
        refund.put("payment_intent", intent.get("id"));
        refund.put("amount", intent.get("amount"));
        refund.put("reason", form.get("reason"));
        refund.put("status", "succeeded");
        refunds.put(id, refund);
        return new Object[]{200, json.writeValueAsString(refund)};
    }

    /** Hides internal "_" fields; latest_charge is expanded only when requested via expand[]. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> view(Map<String, Object> intent, boolean expandCharge) {
        Map<String, Object> out = new LinkedHashMap<>();
        intent.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                out.put(k, v);
            }
        });
        Map<String, Object> charge = (Map<String, Object>) intent.get("_charge");
        out.put("latest_charge", charge == null ? null : expandCharge ? charge : charge.get("id"));
        return out;
    }

    private String error(String message) throws IOException {
        return json.writeValueAsString(Map.of("error", Map.of("message", message, "type", "invalid_request_error")));
    }

    private static Map<String, String> parse(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            String[] kv = pair.split("=", 2);
            values.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** One line per intent, for assertion failure messages. */
    public synchronized List<String> summary() {
        List<String> lines = new ArrayList<>();
        intents.values().forEach(i -> lines.add(i.get("id") + " " + i.get("status") + " " + i.get("amount") + " " + i.get("currency")));
        return lines;
    }
}
