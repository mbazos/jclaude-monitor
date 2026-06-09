package com.mbazos.jclaude.service;

import com.mbazos.jclaude.json.JsonParser;
import com.mbazos.jclaude.json.JsonPath;
import com.mbazos.jclaude.model.WebUsageResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Calls the undocumented claude.ai internal usage API to retrieve 5-hour and
 * 7-day utilisation data without consuming API tokens.
 * Must only be called on a background thread (never the EDT).
 */
public class ClaudeWebClient {

    private static final String URL_TEMPLATE =
            "https://claude.ai/api/organizations/%s/usage";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final String sessionKey;
    private final String orgId;

    public ClaudeWebClient(String sessionKey, String orgId) {
        this.sessionKey = sessionKey;
        this.orgId      = orgId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getOrgId() {
        return orgId;
    }

    /**
     * Fetches current usage from claude.ai.
     * Returns {@link WebUsageResult.Available} on success or
     * {@link WebUsageResult.Unavailable} with a human-readable reason on failure.
     */
    public WebUsageResult fetch() throws IOException, InterruptedException {
        String url = String.format(URL_TEMPLATE, orgId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Cookie",     "sessionKey=" + sessionKey)
                .header("User-Agent", USER_AGENT)
                .header("Accept",     "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return new WebUsageResult.Unavailable(
                    "Session expired or invalid (HTTP " + status + ")");
        }
        if (status >= 400) {
            return new WebUsageResult.Unavailable("HTTP " + status);
        }

        return parseResponse(response.body());
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private WebUsageResult parseResponse(String body) {
        try {
            Object parsed = JsonParser.parse(body);

            Double fiveHUtil      = JsonPath.getDouble(parsed, "five_hour", "utilization");
            String fiveHResetStr  = JsonPath.getString(parsed, "five_hour", "resets_at");
            Double sevenDUtil     = JsonPath.getDouble(parsed, "seven_day", "utilization");
            String sevenDResetStr = JsonPath.getString(parsed, "seven_day", "resets_at");

            // resets_at can legitimately be null when the window hasn't been used yet
            if (fiveHUtil == null || sevenDUtil == null) {
                return new WebUsageResult.Unavailable("Unexpected response format");
            }

            // resets_at uses "+00:00" offset notation, not "Z" — requires OffsetDateTime
            Instant fiveHReset  = parseInstant(fiveHResetStr);
            Instant sevenDReset = parseInstant(sevenDResetStr);

            Boolean extraEnabled  = JsonPath.getBoolean(parsed, "extra_usage", "is_enabled");
            Long    monthlyLimit  = JsonPath.getLong(parsed,    "extra_usage", "monthly_limit");
            Double  usedCredits   = JsonPath.getDouble(parsed,  "extra_usage", "used_credits");

            return new WebUsageResult.Available(
                    fiveHUtil,
                    fiveHReset,
                    sevenDUtil,
                    sevenDReset,
                    Boolean.TRUE.equals(extraEnabled),
                    monthlyLimit != null ? monthlyLimit.intValue() : 0,
                    usedCredits  != null ? usedCredits             : 0.0,
                    Instant.now()
            );
        } catch (Exception e) {
            return new WebUsageResult.Unavailable("Parse error: " + e.getMessage());
        }
    }

    // Returns Instant.now() when the value is null (window not yet started) or unparseable.
    private static Instant parseInstant(String value) {
        if (value == null) return Instant.now();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}
