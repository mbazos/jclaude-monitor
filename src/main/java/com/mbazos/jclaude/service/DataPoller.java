package com.mbazos.jclaude.service;

import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.WebUsageResult;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Manages the 60-second polling cycle on a background daemon thread.
 * Results are posted back to the EDT via {@link SwingUtilities#invokeLater}.
 */
public class DataPoller {

    private static final int POLL_INTERVAL_SECONDS = 60;

    private final ScheduledExecutorService scheduler;
    private volatile ClaudeWebClient webClient;  // null if no session configured
    private final LocalDataReader localReader;
    private final BiConsumer<LocalStats, WebUsageResult> resultHandler;

    public DataPoller(ClaudeWebClient webClient, LocalDataReader localReader,
                      BiConsumer<LocalStats, WebUsageResult> resultHandler) {
        this.webClient     = webClient;
        this.localReader   = localReader;
        this.resultHandler = Objects.requireNonNull(resultHandler, "resultHandler must not be null");
        this.scheduler     = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "quota-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts 60-second polling with an immediate first poll. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::doPoll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Fires an immediate poll without waiting for the 60-second cycle. */
    public void refreshNow() {
        scheduler.execute(this::doPoll);
    }

    /** Updates the web client (e.g. user configured a new session key). */
    public void updateWebClient(ClaudeWebClient newClient) {
        this.webClient = newClient;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void doPoll() {
        LocalStats stats;
        try {
            stats = localReader.readStats();
        } catch (Exception e) {
            stats = null;
        }

        ClaudeWebClient snap = webClient;
        WebUsageResult usage;
        if (snap != null) {
            try {
                usage = snap.fetch();
            } catch (Exception e) {
                usage = new WebUsageResult.Unavailable("Fetch error: " + e.getMessage());
            }
        } else {
            usage = new WebUsageResult.Unavailable("No session configured");
        }

        final LocalStats finalStats = stats;
        final WebUsageResult finalUsage = usage;
        SwingUtilities.invokeLater(() -> resultHandler.accept(finalStats, finalUsage));
    }
}
