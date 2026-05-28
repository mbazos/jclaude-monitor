package com.mbazos.jclaude.service;

import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.PollResult;
import com.mbazos.jclaude.model.QuotaResult;
import com.mbazos.jclaude.service.LocalDataReader.LocalDataResult;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.Map;
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
    private volatile AnthropicApiClient apiClient;  // null if LOCAL_ONLY mode
    private final LocalDataReader localReader;
    private final BiConsumer<LocalStats, PollResult> resultHandler;
    private volatile double budgetUSD = 0.0;

    public DataPoller(AnthropicApiClient apiClient, LocalDataReader localReader,
                      BiConsumer<LocalStats, PollResult> resultHandler) {
        this.apiClient      = apiClient;
        this.localReader    = localReader;
        this.resultHandler  = Objects.requireNonNull(resultHandler, "resultHandler must not be null");
        this.scheduler      = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "quota-poller");
            t.setDaemon(true);
            return t;
        });
    }

    public void setBudget(double budgetUSD) {
        this.budgetUSD = budgetUSD;
    }

    /**
     * Starts 60-second polling with an immediate first poll.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::doPoll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Fires an immediate poll without waiting for the 60-second cycle.
     */
    public void refreshNow() {
        scheduler.execute(this::doPoll);
    }

    /**
     * Updates the API client (e.g. user configured a new API key).
     */
    public void updateApiClient(AnthropicApiClient newClient) {
        this.apiClient = newClient;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void doPoll() {
        try {
            LocalDataResult data = localReader.readAll();
            PollResult pollResult = buildPollResult(data.monthlyTokens());
            final LocalStats stats = data.stats();
            SwingUtilities.invokeLater(() -> resultHandler.accept(stats, pollResult));
        } catch (Exception e) {
            PollResult failure = new PollResult.Failure(
                    "Internal error: " + e.getMessage(), e, Instant.now());
            SwingUtilities.invokeLater(() -> resultHandler.accept(null, failure));
        }
    }

    private PollResult buildPollResult(Map<String, long[]> monthlyTokens) {
        AnthropicApiClient client = apiClient;
        double currentBudget = budgetUSD;
        if (client != null) {
            try {
                QuotaResult quota = client.probe(monthlyTokens, currentBudget);
                return new PollResult.Success(quota);
            } catch (Exception e) {
                return new PollResult.Failure(e.getMessage(), e, Instant.now());
            }
        } else {
            return new PollResult.Success(new QuotaResult.Unavailable("No API key configured"));
        }
    }
}
