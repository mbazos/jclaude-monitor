package com.mbazos.jclaude.service;

import com.mbazos.jclaude.json.JsonParser;
import com.mbazos.jclaude.json.JsonPath;
import com.mbazos.jclaude.model.DailyStats;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.SessionInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code ~/.claude/} data files using {@link JsonParser}.
 */
public class LocalDataReader {

    /** Combined result of a single stats-cache.json parse. */
    record LocalDataResult(LocalStats stats, Map<String, long[]> monthlyTokens) {}

    private static final Path CLAUDE_DIR   = Path.of(System.getProperty("user.home"), ".claude");
    private static final Path SESSIONS_DIR = CLAUDE_DIR.resolve("sessions");
    private static final Path STATS_CACHE  = CLAUDE_DIR.resolve("stats-cache.json");

    /** Five minutes in milliseconds. */
    private static final long ACTIVE_SESSION_WINDOW_MS = 5 * 60 * 1_000;

    /**
     * Parses stats-cache.json exactly once and returns both {@link LocalStats} and
     * the per-model monthly token breakdown.  Prefer this over calling
     * {@link #readStats()} and {@link #readMonthlyTokens()} separately.
     */
    public LocalDataResult readAll() {
        List<SessionInfo> activeSessions = readActiveSessions();
        Object statsRoot = parseFile(STATS_CACHE);
        LocalStats stats = buildLocalStats(activeSessions, statsRoot);
        Map<String, long[]> monthlyTokens = buildMonthlyTokens(statsRoot);
        return new LocalDataResult(stats, monthlyTokens);
    }

    /**
     * Reads current sessions and daily/total stats from the Claude data directory.
     * Degrades gracefully if files are missing or unparseable.
     * Delegates to {@link #readAll()} — prefer that method to avoid double-parsing.
     */
    public LocalStats readStats() {
        return readAll().stats();
    }

    /**
     * Returns per-model token breakdown for the current month.
     * model name → [inputTokens, outputTokens, cacheReadTokens, cacheCreateTokens]
     * Note: stats-cache.json stores only a single total token count per model per day,
     * so the total is placed in inputTokens and the rest are 0.
     * Delegates to {@link #readAll()} — prefer that method to avoid double-parsing.
     */
    public Map<String, long[]> readMonthlyTokens() {
        return readAll().monthlyTokens();
    }

    // -------------------------------------------------------------------------
    // Private — stats-cache.json extraction
    // -------------------------------------------------------------------------

    private LocalStats buildLocalStats(List<SessionInfo> activeSessions, Object statsRoot) {
        DailyStats today = null;
        long totalSessions = 0;
        long totalMessages = 0;

        if (statsRoot != null) {
            // Read totals from root
            Long ts = JsonPath.getLong(statsRoot, "totalSessions");
            Long tm = JsonPath.getLong(statsRoot, "totalMessages");
            if (ts != null) totalSessions = ts;
            if (tm != null) totalMessages = tm;

            // Find today's entry in dailyActivity
            String todayStr = LocalDate.now().toString();
            List<Object> dailyActivity = JsonPath.getList(statsRoot, "dailyActivity");
            if (dailyActivity != null) {
                for (Object item : dailyActivity) {
                    String date = JsonPath.getString(item, "date");
                    if (todayStr.equals(date)) {
                        today = parseDailyStats(item);
                        break;
                    }
                }
            }

            // If today's DailyStats is found but totalTokens needs to come from dailyModelTokens
            if (today != null) {
                long tokensToday = sumTokensForDate(statsRoot, todayStr);
                // Rebuild with correct totalTokens
                today = new DailyStats(today.date(), today.messageCount(), today.sessionCount(),
                        today.toolCallCount(), tokensToday);
            }
        }

        return new LocalStats(activeSessions, today, totalSessions, totalMessages, Instant.now());
    }

    private Map<String, long[]> buildMonthlyTokens(Object statsRoot) {
        Map<String, long[]> result = new HashMap<>();
        if (statsRoot == null) return result;

        String currentMonth = YearMonth.now().toString(); // "YYYY-MM"
        List<Object> dailyModelTokens = JsonPath.getList(statsRoot, "dailyModelTokens");
        if (dailyModelTokens == null) return result;

        for (Object entry : dailyModelTokens) {
            String date = JsonPath.getString(entry, "date");
            if (date == null || !date.startsWith(currentMonth)) continue;

            Map<String, Object> tokensByModel = JsonPath.getMap(entry, "tokensByModel");
            if (tokensByModel == null) continue;

            for (Map.Entry<String, Object> modelEntry : tokensByModel.entrySet()) {
                String modelName = modelEntry.getKey();
                Object value = modelEntry.getValue();
                long tokenCount = 0;
                if (value instanceof Long l)    tokenCount = l;
                else if (value instanceof Double d) tokenCount = d.longValue();

                long[] existing = result.getOrDefault(modelName, new long[4]);
                existing[0] += tokenCount; // all stats-cache tokens go to inputTokens
                result.put(modelName, existing);
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<SessionInfo> readActiveSessions() {
        List<SessionInfo> sessions = new ArrayList<>();
        if (!Files.isDirectory(SESSIONS_DIR)) return sessions;

        long cutoffMs = System.currentTimeMillis() - ACTIVE_SESSION_WINDOW_MS;

        try (var stream = Files.list(SESSIONS_DIR)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                          if (attrs.lastModifiedTime().toMillis() < cutoffMs) return;

                          String content = Files.readString(path);
                          Object root = JsonParser.parse(content);

                          String sessionId = JsonPath.getString(root, "sessionId");
                          String cwd       = JsonPath.getString(root, "cwd");
                          String statusStr = JsonPath.getString(root, "status");
                          Long startedAt   = JsonPath.getLong(root, "startedAt");

                          if (sessionId == null || cwd == null || startedAt == null) return;

                          sessions.add(new SessionInfo(
                                  sessionId,
                                  cwd,
                                  Optional.ofNullable(statusStr),
                                  Instant.ofEpochMilli(startedAt)));
                      } catch (Exception ignored) {
                          // Degrade gracefully on malformed/unreadable session files
                      }
                  });
        } catch (IOException ignored) {
            // Degrade gracefully if sessions directory can't be listed
        }

        return sessions;
    }

    private DailyStats parseDailyStats(Object item) {
        String date = JsonPath.getString(item, "date");
        if (date == null) return null;
        Long messageCount  = JsonPath.getLong(item, "messageCount");
        Long sessionCount  = JsonPath.getLong(item, "sessionCount");
        Long toolCallCount = JsonPath.getLong(item, "toolCallCount");
        return new DailyStats(
                date,
                messageCount  != null ? messageCount.intValue()  : 0,
                sessionCount  != null ? sessionCount.intValue()  : 0,
                toolCallCount != null ? toolCallCount.intValue() : 0,
                0L); // totalTokens filled in by caller
    }

    private long sumTokensForDate(Object statsRoot, String date) {
        List<Object> dailyModelTokens = JsonPath.getList(statsRoot, "dailyModelTokens");
        if (dailyModelTokens == null) return 0;

        for (Object entry : dailyModelTokens) {
            if (!date.equals(JsonPath.getString(entry, "date"))) continue;
            Map<String, Object> tokensByModel = JsonPath.getMap(entry, "tokensByModel");
            if (tokensByModel == null) return 0;
            long sum = 0;
            for (Object v : tokensByModel.values()) {
                if (v instanceof Long l)    sum += l;
                else if (v instanceof Double d) sum += d.longValue();
            }
            return sum;
        }
        return 0;
    }

    private Object parseFile(Path path) {
        try {
            return JsonParser.parse(Files.readString(path));
        } catch (Exception ignored) {
            return null;
        }
    }
}
