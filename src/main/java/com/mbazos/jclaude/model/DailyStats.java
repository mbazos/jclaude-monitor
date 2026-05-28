package com.mbazos.jclaude.model;

public record DailyStats(
        String date,         // "YYYY-MM-DD"
        int messageCount,
        int sessionCount,
        int toolCallCount,
        long totalTokens     // sum across all models for this day
) {}
