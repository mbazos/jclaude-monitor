package com.mbazos.jclaude.util;

/**
 * Minimal logging helper. Verbose diagnostics are gated behind the
 * {@code -Djclaude.debug=true} system property; warnings always print.
 */
public final class Debug {

    private static final boolean ENABLED = Boolean.getBoolean("jclaude.debug");

    private Debug() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /** Verbose diagnostic — printed only when -Djclaude.debug=true. */
    public static void log(String tag, String message) {
        if (ENABLED) {
            System.err.println("[" + tag + "] " + message);
        }
    }

    /** Genuine problem the user may need to know about — always printed. */
    public static void warn(String tag, String message) {
        System.err.println("[" + tag + "] WARN " + message);
    }
}
