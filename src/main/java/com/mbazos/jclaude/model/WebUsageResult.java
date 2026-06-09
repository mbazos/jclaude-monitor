package com.mbazos.jclaude.model;

import java.time.Instant;

public sealed interface WebUsageResult
        permits WebUsageResult.Available, WebUsageResult.Unavailable {

    record Available(
            double fiveHourUtil,       // 0–100 percentage
            Instant fiveHourReset,
            double sevenDayUtil,       // 0–100 percentage
            Instant sevenDayReset,
            boolean extraUsageEnabled,
            int monthlyLimitUsd,
            double usedCreditsUsd,
            Instant lastSynced
    ) implements WebUsageResult {}

    record Unavailable(String reason) implements WebUsageResult {}
}
