package com.mbazos.jclaude.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Manages {@code ~/.jclaude-monitor/config.properties}.
 * <p>
 * All methods are static. The file is read or written on every call so that
 * external edits are always picked up without restarting the application.
 * <p>
 * Property keys:
 * <ul>
 *   <li>{@code apiKey.encrypted}  — Base64-encoded AES-256-GCM ciphertext</li>
 *   <li>{@code budget.monthly}    — decimal string, e.g. "500.00"</li>
 *   <li>{@code window.x}, {@code window.y}, {@code window.w}, {@code window.h} — integers</li>
 *   <li>{@code window.alwaysOnTop} — "true" / "false"</li>
 * </ul>
 */
public final class AppConfig {

    private static final Path CONFIG_DIR  =
            Path.of(System.getProperty("user.home"), ".jclaude-monitor");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    // Property key constants
    private static final String KEY_API_KEY       = "apiKey.encrypted";
    private static final String KEY_BUDGET        = "budget.monthly";
    private static final String KEY_WINDOW_X      = "window.x";
    private static final String KEY_WINDOW_Y      = "window.y";
    private static final String KEY_WINDOW_W      = "window.w";
    private static final String KEY_WINDOW_H      = "window.h";
    private static final String KEY_ALWAYS_ON_TOP = "window.alwaysOnTop";

    private AppConfig() {}

    // -------------------------------------------------------------------------
    // Config directory
    // -------------------------------------------------------------------------

    /** Creates the config directory if it does not already exist. */
    public static void ensureConfigDir() throws IOException {
        Files.createDirectories(CONFIG_DIR);
    }

    // -------------------------------------------------------------------------
    // API key
    // -------------------------------------------------------------------------

    /**
     * Returns the decrypted API key, or {@code null} if none is stored.
     */
    public static String loadApiKey() throws Exception {
        Properties props = load();
        String encrypted = props.getProperty(KEY_API_KEY);
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        return CryptoUtil.decrypt(encrypted);
    }

    /**
     * Encrypts {@code rawKey} and persists it in config.properties.
     */
    public static void saveApiKey(String rawKey) throws Exception {
        Properties props = load();
        props.setProperty(KEY_API_KEY, CryptoUtil.encrypt(rawKey));
        save(props);
    }

    /**
     * Removes the encrypted API key property, effectively switching the
     * application to LOCAL_ONLY mode.
     */
    public static void clearApiKey() throws IOException {
        Properties props = load();
        props.remove(KEY_API_KEY);
        save(props);
    }

    // -------------------------------------------------------------------------
    // Budget
    // -------------------------------------------------------------------------

    /**
     * Returns the monthly budget in USD, or {@code 0.0} if not configured.
     */
    public static double loadBudget() {
        try {
            Properties props = load();
            String value = props.getProperty(KEY_BUDGET);
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            System.err.println("[jclaude-monitor] Failed to read config value: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Persists the monthly budget (in USD) to config.properties.
     */
    public static void saveBudget(double budgetUSD) throws IOException {
        Properties props = load();
        props.setProperty(KEY_BUDGET, String.format("%.2f", budgetUSD));
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
            System.err.println("[jclaude-monitor] Failed to read config value: " + e.getMessage());
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
            System.err.println("[jclaude-monitor] Failed to read config value: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

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
