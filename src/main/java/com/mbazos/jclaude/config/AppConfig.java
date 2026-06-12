package com.mbazos.jclaude.config;

import com.mbazos.jclaude.util.Debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Properties;

/**
 * Manages {@code ~/.jclaude-monitor/config.properties}.
 * <p>
 * All methods are static. The file is read or written on every call so that
 * external edits are always picked up without restarting the application.
 * <p>
 * Property keys:
 * <ul>
 *   <li>{@code session.key.encrypted} — Base64-encoded AES-256-GCM ciphertext of the sessionKey cookie</li>
 *   <li>{@code crypto.salt} — Base64-encoded random PBKDF2 salt for the session key</li>
 *   <li>{@code session.orgId} — plain-text organisation UUID</li>
 *   <li>{@code window.x}, {@code window.y}, {@code window.w}, {@code window.h} — integers</li>
 *   <li>{@code window.alwaysOnTop} — "true" / "false"</li>
 * </ul>
 */
public final class AppConfig {

    private static final Path CONFIG_DIR  =
            Path.of(System.getProperty("user.home"), ".jclaude-monitor");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    // Property key constants
    private static final String KEY_SESSION_KEY     = "session.key.encrypted";
    private static final String KEY_CRYPTO_SALT     = "crypto.salt";
    private static final String KEY_SESSION_ORG_ID  = "session.orgId";
    private static final String KEY_WINDOW_X        = "window.x";
    private static final String KEY_WINDOW_Y        = "window.y";
    private static final String KEY_WINDOW_W        = "window.w";
    private static final String KEY_WINDOW_H        = "window.h";
    private static final String KEY_ALWAYS_ON_TOP     = "window.alwaysOnTop";
    private static final String KEY_MINIMIZE_TO_TRAY  = "window.minimizeToTray";

    private AppConfig() {}

    // -------------------------------------------------------------------------
    // Config directory
    // -------------------------------------------------------------------------

    /** Creates the config directory if it does not already exist. */
    public static void ensureConfigDir() throws IOException {
        Files.createDirectories(CONFIG_DIR);
    }

    // -------------------------------------------------------------------------
    // Session key (claude.ai browser session)
    // -------------------------------------------------------------------------

    /**
     * Returns the decrypted claude.ai session key, or {@code null} if none is
     * stored or the stored value cannot be decrypted (in which case the dead
     * ciphertext is removed so the app cleanly starts in a "not logged in" state).
     * <p>
     * Installs written before the salt was stored ({@code crypto.salt} absent)
     * are migrated transparently: the key is decrypted with the legacy
     * machine-derived salt and re-encrypted with a fresh random one.
     */
    public static String loadSessionKey() {
        Properties props;
        String encrypted;
        try {
            props = load();
            encrypted = props.getProperty(KEY_SESSION_KEY);
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Failed to read config: " + e.getMessage());
            return null;
        }
        if (encrypted == null || encrypted.isBlank()) return null;

        String storedSalt = props.getProperty(KEY_CRYPTO_SALT);
        try {
            if (storedSalt != null && !storedSalt.isBlank()) {
                return CryptoUtil.decrypt(encrypted, Base64.getDecoder().decode(storedSalt.trim()));
            }
            // Pre-salt install: decrypt with the legacy salt, re-encrypt with a new one.
            String plaintext = CryptoUtil.decrypt(encrypted, CryptoUtil.legacySalt());
            saveSessionKey(plaintext);
            return plaintext;
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Stored session key could not be decrypted"
                    + " — clearing it; please log in again (" + e.getMessage() + ")");
            try {
                clearSessionKey();
            } catch (IOException io) {
                Debug.warn("jclaude-monitor", "Failed to clear dead session key: " + io.getMessage());
            }
            return null;
        }
    }

    /** Encrypts {@code rawKey} and persists it in config.properties. */
    public static void saveSessionKey(String rawKey) throws Exception {
        Properties props = load();
        byte[] salt = loadOrCreateSalt(props);
        props.setProperty(KEY_SESSION_KEY, CryptoUtil.encrypt(rawKey, salt));
        save(props);
    }

    /** Removes the stored session key. */
    public static void clearSessionKey() throws IOException {
        Properties props = load();
        props.remove(KEY_SESSION_KEY);
        save(props);
    }

    // -------------------------------------------------------------------------
    // Session org ID (plain text, not sensitive)
    // -------------------------------------------------------------------------

    /** Returns the stored organisation UUID, or {@code null} if none is stored. */
    public static String loadSessionOrgId() {
        try {
            Properties props = load();
            String v = props.getProperty(KEY_SESSION_ORG_ID);
            return (v != null && !v.isBlank()) ? v.trim() : null;
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Failed to read session orgId: " + e.getMessage());
            return null;
        }
    }

    /** Persists the organisation UUID. */
    public static void saveSessionOrgId(String orgId) throws IOException {
        Properties props = load();
        props.setProperty(KEY_SESSION_ORG_ID, orgId.trim());
        save(props);
    }

    /** Removes the stored organisation UUID. */
    public static void clearSessionOrgId() throws IOException {
        Properties props = load();
        props.remove(KEY_SESSION_ORG_ID);
        save(props);
    }

    // -------------------------------------------------------------------------
    // Window state
    // -------------------------------------------------------------------------

    /**
     * Persists the window geometry and always-on-top preference.
     */
    public static void saveWindowState(int x, int y, int w, int h, boolean alwaysOnTop)
            throws IOException {
        Properties props = load();
        props.setProperty(KEY_WINDOW_X, String.valueOf(x));
        props.setProperty(KEY_WINDOW_Y, String.valueOf(y));
        props.setProperty(KEY_WINDOW_W, String.valueOf(w));
        props.setProperty(KEY_WINDOW_H, String.valueOf(h));
        props.setProperty(KEY_ALWAYS_ON_TOP, String.valueOf(alwaysOnTop));
        save(props);
    }

    /**
     * Returns {@code [x, y, w, h]} from the stored window state, or
     * {@code null} if none has been saved yet.
     */
    public static int[] loadWindowState() {
        try {
            Properties props = load();
            String sx = props.getProperty(KEY_WINDOW_X);
            String sy = props.getProperty(KEY_WINDOW_Y);
            String sw = props.getProperty(KEY_WINDOW_W);
            String sh = props.getProperty(KEY_WINDOW_H);
            if (sx == null || sy == null || sw == null || sh == null) {
                return null;
            }
            return new int[]{
                    Integer.parseInt(sx.trim()),
                    Integer.parseInt(sy.trim()),
                    Integer.parseInt(sw.trim()),
                    Integer.parseInt(sh.trim())
            };
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Failed to read config value: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the stored always-on-top preference, defaulting to {@code false}.
     */
    public static boolean loadAlwaysOnTop() {
        try {
            Properties props = load();
            return Boolean.parseBoolean(props.getProperty(KEY_ALWAYS_ON_TOP, "false"));
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Failed to read config value: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the stored minimize-to-tray preference, defaulting to {@code false}.
     */
    public static boolean loadMinimizeToTray() {
        try {
            Properties props = load();
            return Boolean.parseBoolean(props.getProperty(KEY_MINIMIZE_TO_TRAY, "false"));
        } catch (Exception e) {
            Debug.warn("jclaude-monitor", "Failed to read config value: " + e.getMessage());
            return false;
        }
    }

    /** Persists the minimize-to-tray preference. */
    public static void saveMinimizeToTray(boolean value) throws IOException {
        Properties props = load();
        props.setProperty(KEY_MINIMIZE_TO_TRAY, String.valueOf(value));
        save(props);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Returns the stored PBKDF2 salt, generating and recording one in
     *  {@code props} on first use. The caller is responsible for saving. */
    private static byte[] loadOrCreateSalt(Properties props) {
        String stored = props.getProperty(KEY_CRYPTO_SALT);
        if (stored != null && !stored.isBlank()) {
            return Base64.getDecoder().decode(stored.trim());
        }
        byte[] salt = CryptoUtil.generateSalt();
        props.setProperty(KEY_CRYPTO_SALT, Base64.getEncoder().encodeToString(salt));
        return salt;
    }

    /** Reads config.properties into a {@link Properties} object. Returns an empty
     *  Properties if the file does not exist yet. */
    private static Properties load() throws IOException {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            }
        }
        return props;
    }

    /** Writes {@code props} to config.properties, creating it if necessary. */
    private static void save(Properties props) throws IOException {
        ensureConfigDir();
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            props.store(out, "jclaude-monitor configuration");
        }
    }
}
