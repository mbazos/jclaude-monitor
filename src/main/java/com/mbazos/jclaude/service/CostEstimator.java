package com.mbazos.jclaude.service;

import java.util.Map;

public class CostEstimator {

    // Pricing per million tokens: [inputPerM, outputPerM, cacheReadPerM, cacheCreatePerM]
    // Prices as of 2026 — update source if Anthropic changes pricing
    private static final Map<String, double[]> PRICING = Map.of(
        "claude-sonnet-4-6",  new double[]{3.00, 15.00, 0.30, 3.75},
        "claude-opus-4-7",    new double[]{15.00, 75.00, 1.50, 18.75},
        "claude-haiku-4-5",   new double[]{0.80, 4.00, 0.08, 1.00}
    );

    // Default fallback for unknown models (Sonnet pricing)
    private static final double[] DEFAULT_PRICING = new double[]{3.00, 15.00, 0.30, 3.75};

    /**
     * Estimates total USD cost from per-model token counts.
     *
     * @param tokensByModel model name → [inputTokens, outputTokens, cacheReadTokens, cacheCreateTokens]
     * @return total estimated USD cost
     */
    public double estimateCost(Map<String, long[]> tokensByModel) {
        double total = 0.0;
        for (Map.Entry<String, long[]> entry : tokensByModel.entrySet()) {
            double[] pricing = findPricing(entry.getKey());
            long[] tokens = entry.getValue();
            long inputTokens      = tokens.length > 0 ? tokens[0] : 0;
            long outputTokens     = tokens.length > 1 ? tokens[1] : 0;
            long cacheReadTokens  = tokens.length > 2 ? tokens[2] : 0;
            long cacheCreateTokens = tokens.length > 3 ? tokens[3] : 0;

            total += (inputTokens      / 1_000_000.0) * pricing[0];
            total += (outputTokens     / 1_000_000.0) * pricing[1];
            total += (cacheReadTokens  / 1_000_000.0) * pricing[2];
            total += (cacheCreateTokens / 1_000_000.0) * pricing[3];
        }
        return total;
    }

    /**
     * Finds pricing for the given model name by checking if the name contains
     * any of the known pricing keys (e.g. "claude-sonnet-4-6-20251231" → "claude-sonnet-4-6").
     */
    private double[] findPricing(String modelName) {
        for (Map.Entry<String, double[]> entry : PRICING.entrySet()) {
            if (modelName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_PRICING;
    }
}
