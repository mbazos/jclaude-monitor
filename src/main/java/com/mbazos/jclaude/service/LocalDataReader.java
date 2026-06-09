package com.mbazos.jclaude.service;

import com.mbazos.jclaude.json.JsonParser;
import com.mbazos.jclaude.json.JsonPath;
import com.mbazos.jclaude.model.LocalStats;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads all-time totals from {@code ~/.claude/stats-cache.json}.
 */
public class LocalDataReader {

    private static final Path STATS_CACHE =
            Path.of(System.getProperty("user.home"), ".claude", "stats-cache.json");

    public LocalStats readStats() {
        try {
            Object root = JsonParser.parse(Files.readString(STATS_CACHE));
            Long ts = JsonPath.getLong(root, "totalSessions");
            Long tm = JsonPath.getLong(root, "totalMessages");
            return new LocalStats(ts != null ? ts : 0L, tm != null ? tm : 0L);
        } catch (Exception ignored) {
            return new LocalStats(0L, 0L);
        }
    }
}
