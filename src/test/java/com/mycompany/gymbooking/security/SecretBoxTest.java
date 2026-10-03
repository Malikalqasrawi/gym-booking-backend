package com.mycompany.gymbooking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private static String key(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("values are encrypted with a new nonce each time and decrypt back")
    void roundTrip() {
        SecretBox box = new SecretBox(key(1));
        String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        String sealed = box.seal(secret);
        assertTrue(sealed.startsWith("v1:"));
        assertTrue(!sealed.contains(secret));
        assertNotEquals(sealed, box.seal(secret), "a random nonce per value");
        assertEquals(secret, box.open(sealed));
        assertNull(box.seal(null));
        assertNull(box.open(null));
    }

    @Test
    @DisplayName("values saved before encryption are read as they are; another key can't read new ones")
    void legacyAndWrongKey() {
        SecretBox box = new SecretBox(key(1));
        assertEquals("JBSWY3DPEHPK3PXP", box.open("JBSWY3DPEHPK3PXP"));
        String sealed = box.seal("JBSWY3DPEHPK3PXP");
        assertThrows(IllegalStateException.class, () -> new SecretBox(key(2)).open(sealed));
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThrows(IllegalStateException.class, () -> box.open(tampered), "changed data is detected");
    }

    @Test
    @DisplayName("the app doesn't start without a proper 32-byte key")
    void keyIsRequired() {
        assertThrows(IllegalStateException.class, () -> new SecretBox(""));
        assertThrows(IllegalStateException.class, () -> new SecretBox("not base64!"));
        assertThrows(IllegalStateException.class, () -> new SecretBox(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
