package com.mbazos.jclaude.model;

public enum PlanType {
    STANDARD,           // has anthropic-ratelimit-unified-5h-utilization header
    ENTERPRISE_TOKENS,  // has anthropic-ratelimit-workspace-tokens-remaining header
    ENTERPRISE_BUDGET,  // no quota headers → estimate from local token data
    LOCAL_ONLY          // no API key configured
}
