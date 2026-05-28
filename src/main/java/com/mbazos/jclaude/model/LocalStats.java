package com.mbazos.jclaude.model;

import java.time.Instant;
import java.util.List;

public record LocalStats(
        List<SessionInfo> activeSessions,
        DailyStats today,        // null if no entry for today in stats-cache
        long totalSessions,
        long totalMessages,
        Instant lastRead
) {}
