package com.mbazos.jclaude.model;

public record DailyStats(
        String date,         // "YYYY-MM-DD"
        int messageCount,
        int sessionCount,
        int toolCallCount,
        long totalTokens     // sum across all models for this day
) {
    public DailyStats {
        if (date != null && !date.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("date must be YYYY-MM-DD, got: " + date);
    }
}
