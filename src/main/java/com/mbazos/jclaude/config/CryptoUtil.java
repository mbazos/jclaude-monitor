package com.mbazos.jclaude.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption for the session key.
 * Uses PBKDF2WithHmacSHA256 key derivation — no external dependencies.
 * <p>
 * The password is a hardcoded constant, so this is at-rest obfuscation rather
 * than strong secrecy. The PBKDF2 salt is random, generated once per install
 * and stored alongside the ciphertext in config.properties (see
 * {@link AppConfig}). Earlier versions derived the salt from
 * {@code username@hostname}; {@link #legacySalt()} exists only so those
 * installs can be migrated transparently.
 */
final class CryptoUtil {

    private static final char[] PASSWORD =
            "jclaude-monitor-v1".toCharArray();

    private static final int PBKDF2_ITERATIONS = 310_000;
    private static final int KEY_LENGTH_BITS    = 256;
    private static final int IV_LENGTH_BYTES    = 12;
    private static final int GCM_TAG_BITS       = 128;
    private static final int SALT_LENGTH_BYTES  = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns a fresh random salt for key derivation. */
    static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * The pre-1.1 deterministic salt ({@code username@hostname}). Kept only to
     * migrate old installs; remove once migration support is dropped.
     */
    static byte[] legacySalt() throws Exception {
        String username = System.getProperty("user.name");
        String hostname = InetAddress.getLocalHost().getHostName();
        return (username + "@" + hostname).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encrypts {@code plaintext} with AES-256-GCM.
     *
     * @return Base64-encoded string containing IV (12 bytes) || ciphertext
     */
    static String encrypt(String plaintext, byte[] salt) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext   = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] ivPlusCipher = new byte[IV_LENGTH_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, ivPlusCipher, 0, IV_LENGTH_BYTES);
        System.arraycopy(ciphertext, 0, ivPlusCipher, IV_LENGTH_BYTES, ciphertext.length);

        return Base64.getEncoder().encodeToString(ivPlusCipher);
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt(String, byte[])}.
     *
     * @param encoded Base64-encoded IV || ciphertext
     * @return the original plaintext
     */
    static String decrypt(String encoded, byte[] salt) throws Exception {
        byte[] ivPlusCipher = Base64.getDecoder().decode(encoded);

        if (ivPlusCipher.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encoded data too short to contain IV (corrupted config?)");
        }

        byte[] iv         = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[ivPlusCipher.length - IV_LENGTH_BYTES];
        System.arraycopy(ivPlusCipher, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(ivPlusCipher, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Derives an AES-256 key via PBKDF2WithHmacSHA256.
     * Called fresh each time — deterministic, so caching provides no benefit
     * and avoids holding key material in memory longer than necessary.
     */
    private static SecretKey deriveKey(byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(PASSWORD, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, "AES");
    }
}
