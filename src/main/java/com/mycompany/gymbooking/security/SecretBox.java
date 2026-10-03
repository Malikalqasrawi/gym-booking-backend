package com.mycompany.gymbooking.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts values that must be readable by the server but shouldn't be readable from a copy of the
 * database, such as two-factor secrets. AES-256-GCM with a random nonce per value; the key comes from
 * app.security.encryption-key and never touches the database. Stored as "v1:" + Base64(nonce + data).
 */
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${app.security.encryption-key:}") String base64Key) {
        if (base64Key.isBlank()) {
            throw new IllegalStateException("Set app.security.encryption-key in local.properties (ENCRYPTION_KEY for Docker). "
                    + "Create one with: openssl rand -base64 32");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.encryption-key must be Base64. Create one with: openssl rand -base64 32");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("app.security.encryption-key must be 32 bytes. Create one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String seal(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] data = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + data.length)
                    .put(nonce).put(data).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    /** The original value. Values saved before encryption was added are returned as they are. */
    public String open(String sealed) {
        if (sealed == null || !isSealed(sealed)) {
            return sealed;
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a stored secret. Was app.security.encryption-key changed?", e);
        }
    }

    public static boolean isSealed(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
