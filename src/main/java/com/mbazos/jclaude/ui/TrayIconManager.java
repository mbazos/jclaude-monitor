package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.WebUsageResult;
import com.mbazos.jclaude.util.Debug;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static com.mbazos.jclaude.ui.Theme.GREEN;
import static com.mbazos.jclaude.ui.Theme.ORANGE;
import static com.mbazos.jclaude.ui.Theme.RED;
import static com.mbazos.jclaude.ui.Theme.YELLOW;

public class TrayIconManager {

    private static final String TAG = "jclaude-monitor";

    private final JFrame   owner;
    private final TrayIcon trayIcon;

    public TrayIconManager(JFrame owner, Runnable onQuit) {
        this.owner = owner;

        TrayIcon icon = null;
        if (SystemTray.isSupported()) {
            PopupMenu menu = new PopupMenu();

            MenuItem showItem = new MenuItem("Show Window");
            showItem.addActionListener(e -> SwingUtilities.invokeLater(this::showWindow));
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> onQuit.run());
            menu.add(showItem);
            menu.addSeparator();
            menu.add(exitItem);

            TrayIcon ti = new TrayIcon(renderIcon(-1), "jclaude-monitor", menu);
            ti.setImageAutoSize(true);
            ti.addActionListener(e -> SwingUtilities.invokeLater(this::showWindow));
            try {
                SystemTray.getSystemTray().add(ti);
                icon = ti;
            } catch (AWTException e) {
                Debug.warn(TAG, "Failed to add tray icon: " + e.getMessage());
            }
        }
        trayIcon = icon;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns true if a tray icon was successfully installed. */
    public boolean isTrayActive() {
        return trayIcon != null;
    }

    /** Updates the tray icon, tooltip, and taskbar/dock indicators. */
    public void update(WebUsageResult result) {
        switch (result) {
            case WebUsageResult.Available a -> {
                double maxPct = computeMaxPct(a);
                if (trayIcon != null) {
                    trayIcon.setImage(renderIcon(maxPct));
                    trayIcon.setToolTip("jclaude-monitor  " + buildTooltipDetail(a));
                }
                setDockBadge(String.format("%d%%", (int) Math.round(maxPct)));
                setTaskbarProgress((int) Math.round(maxPct));
            }
            case WebUsageResult.Unavailable u -> {
                if (trayIcon != null) {
                    trayIcon.setImage(renderIcon(-1));
                    trayIcon.setToolTip("jclaude-monitor — not connected");
                }
                setDockBadge(null);
                setTaskbarProgress(-1);
            }
        }
    }

    /** Removes the tray icon and clears any taskbar/dock indicators. */
    public void dispose() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        setDockBadge(null);
        setTaskbarProgress(-1);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void showWindow() {
        owner.setVisible(true);
        owner.setState(JFrame.NORMAL);
        // On macOS UIElement apps the process is not the active application, so toFront()
        // alone is ignored by the window manager. Briefly setting alwaysOnTop forces the
        // window to the front, then we restore the user's original preference.
        boolean wasAlwaysOnTop = owner.isAlwaysOnTop();
        if (!wasAlwaysOnTop) owner.setAlwaysOnTop(true);
        owner.toFront();
        owner.requestFocus();
        if (!wasAlwaysOnTop) SwingUtilities.invokeLater(() -> owner.setAlwaysOnTop(false));
    }

    private static double computeMaxPct(WebUsageResult.Available a) {
        double max = 0;
        if (a.fiveHourUtil()  != null) max = Math.max(max, a.fiveHourUtil());
        if (a.sevenDayUtil()  != null) max = Math.max(max, a.sevenDayUtil());
        if (a.extraUsageEnabled())     max = Math.max(max, a.extraUsageUtil());
        return max;
    }

    private static String buildTooltipDetail(WebUsageResult.Available a) {
        if (a.fiveHourUtil() != null && a.sevenDayUtil() != null) {
            return String.format("5h: %d%%  7d: %d%%",
                    (int) Math.round(a.fiveHourUtil()),
                    (int) Math.round(a.sevenDayUtil()));
        }
        if (a.extraUsageEnabled()) {
            return String.format("Budget: %.1f%% used", a.extraUsageUtil());
        }
        return "";
    }

    private static Image renderIcon(double pct) {
        Dimension size;
        try {
            size = SystemTray.isSupported()
                    ? SystemTray.getSystemTray().getTrayIconSize()
                    : new Dimension(22, 22);
        } catch (Exception e) {
            size = new Dimension(22, 22);
        }
        int w = size.width;
        int h = size.height;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color bg;
        String text;
        if (pct < 0) {
            bg   = new Color(0x55, 0x55, 0x55);
            text = "--";
        } else {
            bg   = thresholdColor(pct / 100.0);
            text = String.valueOf((int) Math.round(pct));
        }

        g.setColor(bg);
        g.fillRoundRect(0, 0, w, h, 4, 4);

        g.setColor(Color.WHITE);
        int ptSize = text.length() >= 3 ? (int) (w * 0.38f) : (int) (w * 0.52f);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(7, ptSize)));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, tx, ty);
        g.dispose();
        return img;
    }

    private static Color thresholdColor(double v) {
        if (v < 0.50) return GREEN;
        if (v < 0.75) return YELLOW;
        if (v < 0.90) return ORANGE;
        return RED;
    }

    private void setDockBadge(String badge) {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar tb = Taskbar.getTaskbar();
            if (tb.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
                tb.setIconBadge(badge);
            }
        } catch (Exception e) {
            Debug.warn(TAG, "Dock badge update failed: " + e.getMessage());
        }
    }

    private void setTaskbarProgress(int pct) {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar tb = Taskbar.getTaskbar();
            if (tb.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
                tb.setWindowProgressValue(owner, pct < 0 ? -1 : Math.min(100, pct));
            }
        } catch (Exception e) {
            Debug.warn(TAG, "Taskbar progress update failed: " + e.getMessage());
        }
    }
}
