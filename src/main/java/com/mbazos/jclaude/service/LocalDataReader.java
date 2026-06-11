package com.mbazos.jclaude.service;

import com.mbazos.jclaude.json.JsonParser;
import com.mbazos.jclaude.json.JsonPath;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.util.Debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads all-time totals from {@code ~/.claude/stats-cache.json}.
 */
public class LocalDataReader {

    private static final Path STATS_CACHE =
            Path.of(System.getProperty("user.home"), ".claude", "stats-cache.json");

    private final Path statsCache;

    public LocalDataReader() {
        this(STATS_CACHE);
    }

    LocalDataReader(Path statsCache) {
        this.statsCache = statsCache;
    }

    /**
     * Returns the stats, or {@link Optional#empty()} when the cache file is
     * missing, unreadable, or contains neither expected key — so the UI can
     * show "no data" instead of misleading zeros.
     */
    public Optional<LocalStats> readStats() {
        try {
            Object root = JsonParser.parse(Files.readString(statsCache));
            Long ts = JsonPath.getLong(root, "totalSessions");
            Long tm = JsonPath.getLong(root, "totalMessages");
            if (ts == null && tm == null) {
                return Optional.empty();
            }
            return Optional.of(new LocalStats(ts != null ? ts : 0L, tm != null ? tm : 0L));
        } catch (Exception e) {
            Debug.log("LocalDataReader", "Could not read " + statsCache + ": " + e);
            return Optional.empty();
        }
    }
}
