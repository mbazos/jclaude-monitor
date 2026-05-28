package com.mbazos.jclaude.model;

import java.time.Instant;
import java.util.OptionalDouble;

public sealed interface QuotaResult permits
        QuotaResult.Standard, QuotaResult.EnterpriseTokens,
        QuotaResult.EnterpriseBudget, QuotaResult.Unavailable {

    record Standard(
            double fiveHourUtil,     // 0.0–1.0
            Instant fiveHourReset,
            double sevenDayUtil,     // 0.0–1.0
            Instant sevenDayReset,
            Instant lastSynced
    ) implements QuotaResult {}

    record EnterpriseTokens(
            long remaining,
            long limit,
            Instant lastSynced
    ) implements QuotaResult {}

    record EnterpriseBudget(
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheCreateTokens,
            double estimatedCostUSD,
            OptionalDouble budgetUSD,  // empty if not configured by user
            String month,              // e.g. "MAY 2026"
            Instant lastSynced
    ) implements QuotaResult {}

    record Unavailable(String reason) implements QuotaResult {}
}
