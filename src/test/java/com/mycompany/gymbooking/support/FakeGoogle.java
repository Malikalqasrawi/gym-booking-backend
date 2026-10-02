package com.mycompany.gymbooking.support;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local HTTP server that publishes a signing key the way Google does (a JSON Web Key Set), plus a
 * builder for ID tokens signed with it, so Google sign-in can be tested without Google.
 */
public final class FakeGoogle implements AutoCloseable {

    public static final String CLIENT_ID = "test-web-client.apps.googleusercontent.com";

    private final HttpServer server;
    private final AtomicInteger keyRequests = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile KeyPair keys = Jwts.SIG.RS256.keyPair().build();
    private volatile String keyId = "key-1";

    private FakeGoogle() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/v3/certs", exchange -> {
            keyRequests.incrementAndGet();
            byte[] body = keySetJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    public static FakeGoogle start() throws IOException {
        return new FakeGoogle();
    }

    public String keysUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/v3/certs";
    }

    /** How often the key set was downloaded. */
    public int keyRequests() {
        return keyRequests.get();
    }

    /** Google replaces its signing key now and then; tokens signed with the old one stop working. */
    public void rotateKey() {
        keys = Jwts.SIG.RS256.keyPair().build();
        keyId = "key-" + System.nanoTime();
    }

    /** An ID token for a Google account with a verified email, valid for an hour. */
    public String idToken(String subject, String email) {
        return token(subject, email).sign();
    }

    public Token token(String subject, String email) {
        return new Token(subject, email);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    private String keySetJson() {
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"kid\":\"" + keyId + "\","
                + "\"n\":\"" + base64Url(key.getModulus()) + "\",\"e\":\"" + base64Url(key.getPublicExponent()) + "\"}]}";
    }

    private static String base64Url(BigInteger number) {
        byte[] bytes = number.toByteArray();
        if (bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);   // no sign byte in JWKs
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** An ID token to change before signing, e.g. to make it invalid in one specific way. */
    public final class Token {

        private final String subject;
        private final String email;
        private boolean emailVerified = true;
        private String hostedDomain;
        private String name = "Google User";
        private String audience = CLIENT_ID;
        private String issuer = "https://accounts.google.com";
        private Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
        private KeyPair signingKeys = keys;

        private Token(String subject, String email) {
            this.subject = subject;
            this.email = email;
        }

        public Token emailVerified(boolean value) {
            emailVerified = value;
            return this;
        }

        /** Set for Google Workspace accounts (e.g. a company domain). */
        public Token hostedDomain(String value) {
            hostedDomain = value;
            return this;
        }

        public Token name(String value) {
            name = value;
            return this;
        }

        public Token audience(String value) {
            audience = value;
            return this;
        }

        public Token issuer(String value) {
            issuer = value;
            return this;
        }

        public Token expiresAt(Instant value) {
            expiresAt = value;
            return this;
        }

        /** Signed with a key Google never published, like a forged token. */
        public Token signedWithUnknownKey() {
            signingKeys = Jwts.SIG.RS256.keyPair().build();
            return this;
        }

        public String sign() {
            JwtBuilder builder = Jwts.builder()
                    .header().keyId(keyId).and()
                    .issuer(issuer)
                    .audience().add(audience).and()
                    .subject(subject)
                    .issuedAt(new Date())
                    .expiration(Date.from(expiresAt))
                    .claim("email", email)
                    .claim("email_verified", emailVerified)
                    .claim("name", name);
            if (hostedDomain != null) {
                builder.claim("hd", hostedDomain);
            }
            return builder.signWith(signingKeys.getPrivate(), Jwts.SIG.RS256).compact();
        }
    }
}
