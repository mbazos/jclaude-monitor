package com.mbazos.jclaude.service;

import com.mbazos.jclaude.model.QuotaResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Makes a probe HTTP POST to the Anthropic API and returns a {@link QuotaResult}.
 * Must only be called on a background thread (never the EDT).
 */
public class AnthropicApiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    // Minimal request to trigger rate-limit headers
    private static final String PROBE_BODY = """
            {"model":"claude-haiku-4-5-20251001","max_tokens":1,\
            "messages":[{"role":"user","content":"hi"}]}
            """;

    private final HttpClient httpClient;
    private final String apiKey;
    private final CostEstimator costEstimator = new CostEstimator();

    public AnthropicApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Makes the probe request and returns a {@link QuotaResult}.
     *
     * @param monthlyTokens per-model token breakdown for current month
     *                      (model name → [inputTokens, outputTokens, cacheReadTokens, cacheCreateTokens])
     * @param budgetUSD     monthly budget in USD (0 if not configured)
     */
    public QuotaResult probe(Map<String, long[]> monthlyTokens, double budgetUSD)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(PROBE_BODY))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Check HTTP status before inspecting headers — error responses won't have quota headers
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return new QuotaResult.Unavailable("Invalid API key (HTTP " + status + ")");
        }
        if (status == 429) {
            return new QuotaResult.Unavailable("Rate limited — try again later (HTTP 429)");
        }
        if (status >= 500) {
            return new QuotaResult.Unavailable("Anthropic API unavailable (HTTP " + status + ")");
        }

        // Plan type detection from response headers
        if (response.headers().firstValue("anthropic-ratelimit-unified-5h-utilization").isPresent()) {
            return parseStandard(response);
        } else if (response.headers().firstValue("anthropic-ratelimit-workspace-tokens-remaining").isPresent()) {
            return parseEnterpriseTokens(response);
        } else {
            return buildEnterpriseBudget(monthlyTokens, budgetUSD);
        }
    }

    private QuotaResult parseStandard(HttpResponse<String> response) {
        String fiveHUtilStr  = response.headers().firstValue("anthropic-ratelimit-unified-5h-utilization").orElse(null);
        String fiveHResetStr = response.headers().firstValue("anthropic-ratelimit-unified-5h-reset").orElse(null);
        String sevenDUtilStr = response.headers().firstValue("anthropic-ratelimit-unified-7d-utilization").orElse(null);
        String sevenDResetStr = response.headers().firstValue("anthropic-ratelimit-unified-7d-reset").orElse(null);

        if (fiveHUtilStr == null || fiveHResetStr == null || sevenDUtilStr == null || sevenDResetStr == null) {
            return new QuotaResult.Unavailable("Missing quota headers");
        }

        try {
            double fiveHourUtil   = Double.parseDouble(fiveHUtilStr);
            Instant fiveHourReset = Instant.parse(fiveHResetStr);
            double sevenDayUtil   = Double.parseDouble(sevenDUtilStr);
            Instant sevenDayReset = Instant.parse(sevenDResetStr);
            return new QuotaResult.Standard(fiveHourUtil, fiveHourReset, sevenDayUtil, sevenDayReset, Instant.now());
        } catch (Exception e) {
            return new QuotaResult.Unavailable("Failed to parse quota headers: " + e.getMessage());
        }
    }

    private QuotaResult parseEnterpriseTokens(HttpResponse<String> response) {
        String remainingStr = response.headers().firstValue("anthropic-ratelimit-workspace-tokens-remaining").orElse(null);
        String limitStr     = response.headers().firstValue("anthropic-ratelimit-workspace-tokens-limit").orElse(null);

        if (remainingStr == null || limitStr == null) {
            return new QuotaResult.Unavailable("Missing workspace token headers");
        }

        try {
            long remaining = Long.parseLong(remainingStr);
            long limit     = Long.parseLong(limitStr);
            return new QuotaResult.EnterpriseTokens(remaining, limit, Instant.now());
        } catch (NumberFormatException e) {
            return new QuotaResult.Unavailable("Failed to parse workspace token headers: " + e.getMessage());
        }
    }

    private QuotaResult buildEnterpriseBudget(Map<String, long[]> monthlyTokens, double budgetUSD) {
        long inputTokens       = 0;
        long outputTokens      = 0;
        long cacheReadTokens   = 0;
        long cacheCreateTokens = 0;

        for (long[] tokens : monthlyTokens.values()) {
            inputTokens       += tokens.length > 0 ? tokens[0] : 0;
            outputTokens      += tokens.length > 1 ? tokens[1] : 0;
            cacheReadTokens   += tokens.length > 2 ? tokens[2] : 0;
            cacheCreateTokens += tokens.length > 3 ? tokens[3] : 0;
        }

        double estimatedCostUSD = costEstimator.estimateCost(monthlyTokens);
        OptionalDouble budget = budgetUSD > 0 ? OptionalDouble.of(budgetUSD) : OptionalDouble.empty();

        YearMonth now = YearMonth.now();
        String month = now.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase()
                + " " + now.getYear();

        return new QuotaResult.EnterpriseBudget(
                inputTokens, outputTokens, cacheReadTokens, cacheCreateTokens,
                estimatedCostUSD, budget, month, Instant.now());
    }
}
