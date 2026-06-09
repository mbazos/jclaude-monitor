package com.mbazos.jclaude.model;

import java.time.Instant;

public sealed interface WebUsageResult
        permits WebUsageResult.Available, WebUsageResult.Unavailable {

    record Available(
            Double fiveHourUtil,       // 0–100 percentage, null if not applicable
            Instant fiveHourReset,
            Double sevenDayUtil,       // 0–100 percentage, null if not applicable
            Instant sevenDayReset,
            boolean extraUsageEnabled,
            double extraUsageUtil,     // 0–100 percentage from extra_usage.utilization
            double monthlyLimitCents,  // raw value from API (divide by 100 for dollars)
            double usedCreditsCents,   // raw value from API (divide by 100 for dollars)
            Instant lastSynced
    ) implements WebUsageResult {}

    record Unavailable(String reason) implements WebUsageResult {}
}
