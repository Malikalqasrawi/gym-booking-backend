package com.mycompany.gymbooking.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local HTTP server that answers like Twilio Verify's two endpoints, so the Twilio client can be
 * tested without an account. It records each request; answers can be set up in advance.
 */
public final class FakeTwilio implements AutoCloseable {

    public static final String ACCOUNT_SID = "ACtest";
    public static final String AUTH_TOKEN = "test-token";
    public static final String SERVICE_SID = "VAtest";

    /** A request as Twilio received it. */
    public record Request(String path, String authorization, Map<String, String> form) {
    }

    private record Answer(int status, String json) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Deque<Answer> answers = new ConcurrentLinkedDeque<>();

    private FakeTwilio() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/Services/" + SERVICE_SID + "/", this::handle);
        server.start();
    }

    public static FakeTwilio start() throws IOException {
        return new FakeTwilio();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The next request gets this answer instead of a successful one. */
    public void answerNext(int status, String json) {
        answers.add(new Answer(status, json));
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.add(new Request(path, exchange.getRequestHeaders().getFirst("Authorization"), form));

        Answer answer = answers.poll();
        if (answer == null) {   // success: a code was sent, or the right code (123456) was entered
            answer = path.endsWith("/Verifications")
                    ? new Answer(201, "{\"sid\":\"VEtest\",\"status\":\"pending\",\"channel\":\"sms\"}")
                    : new Answer(200, "{\"status\":\"" + ("123456".equals(form.get("Code")) ? "approved" : "pending") + "\"}");
        }
        byte[] body = answer.json().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                form.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }
}
