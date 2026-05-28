package com.mbazos.jclaude.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Shared visual constants for the jclaude-monitor UI.
 * All fields are {@code public static final}.
 */
public final class Theme {

    private Theme() {}

    // -------------------------------------------------------------------------
    // Colors
    // -------------------------------------------------------------------------

    /** #1a1a1a — application background */
    public static final Color BG_DARK    = new Color(0x1a, 0x1a, 0x1a);

    /** #222222 — section panel background */
    public static final Color BG_SECTION = new Color(0x22, 0x22, 0x22);

    /** White — primary text */
    public static final Color FG_PRIMARY   = new Color(0xff, 0xff, 0xff);

    /** #aaaaaa — secondary / muted text */
    public static final Color FG_SECONDARY = new Color(0xaa, 0xaa, 0xaa);

    /** #d97757 — Claude orange accent */
    public static final Color ACCENT  = new Color(0xd9, 0x77, 0x57);

    /** #333333 — borders and separators */
    public static final Color BORDER  = new Color(0x33, 0x33, 0x33);

    /** Progress bar: utilisation &lt; 50 % */
    public static final Color GREEN  = new Color(0x4c, 0xaf, 0x50);

    /** Progress bar: utilisation &lt; 75 % */
    public static final Color YELLOW = new Color(0xff, 0xc1, 0x07);

    /** Progress bar: utilisation &lt; 90 % (same as ACCENT) */
    public static final Color ORANGE = ACCENT;

    /** Progress bar: utilisation &ge; 90 % */
    public static final Color RED    = new Color(0xf4, 0x43, 0x36);

    // -------------------------------------------------------------------------
    // Fonts
    // -------------------------------------------------------------------------

    public static final Font MONO_PLAIN = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font MONO_BOLD  = new Font(Font.MONOSPACED, Font.BOLD,  12);
    public static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    public static final Font MONO_LARGE = new Font(Font.MONOSPACED, Font.BOLD,  14);
}
