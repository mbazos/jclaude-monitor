package com.mbazos.jclaude.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoUtilTest {

    private static final byte[] SALT = "fixed-test-salt!".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTrip() throws Exception {
        String original  = "sk-ant-test123";
        String encrypted = CryptoUtil.encrypt(original, SALT);
        assertEquals(original, CryptoUtil.decrypt(encrypted, SALT));
    }

    @Test
    void differentIvPerEncryption() throws Exception {
        String original = "sk-ant-test123";
        assertNotEquals(CryptoUtil.encrypt(original, SALT), CryptoUtil.encrypt(original, SALT));
    }

    @Test
    void wrongSaltFailsToDecrypt() throws Exception {
        String encrypted = CryptoUtil.encrypt("sk-ant-test123", SALT);
        byte[] otherSalt = CryptoUtil.generateSalt();
        assertThrows(Exception.class, () -> CryptoUtil.decrypt(encrypted, otherSalt));
    }

    @Test
    void truncatedCiphertextRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decrypt(tooShort, SALT));
    }

    @Test
    void generatedSaltsAreUniqueAndSized() {
        byte[] a = CryptoUtil.generateSalt();
        byte[] b = CryptoUtil.generateSalt();
        assertEquals(16, a.length);
        assertNotEquals(Base64.getEncoder().encodeToString(a),
                        Base64.getEncoder().encodeToString(b));
    }
}
