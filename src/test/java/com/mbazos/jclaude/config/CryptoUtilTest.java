package com.mbazos.jclaude.config;

/**
 * Quick manual verification that CryptoUtil round-trips correctly.
 * Run via: mvn test -pl . -Dtest=CryptoUtilTest
 */
public class CryptoUtilTest {

    public static void main(String[] args) throws Exception {
        String original  = "sk-ant-test123";
        String encrypted = CryptoUtil.encrypt(original);
        String decrypted = CryptoUtil.decrypt(encrypted);

        System.out.println("Original:  " + original);
        System.out.println("Encrypted: " + encrypted);
        System.out.println("Decrypted: " + decrypted);

        if (!original.equals(decrypted)) {
            throw new AssertionError(
                    "Round-trip FAILED: expected [" + original + "] but got [" + decrypted + "]");
        }

        // Different IVs — same plaintext should produce different ciphertext each call
        String encrypted2 = CryptoUtil.encrypt(original);
        if (encrypted.equals(encrypted2)) {
            throw new AssertionError("IV randomness FAILED: two encryptions produced identical output");
        }

        System.out.println("All assertions passed.");
    }
}
