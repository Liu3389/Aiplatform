package com.ai.gateway.service;

/**
 * Central output-token policy shared by every provider adapter.
 * Model capability is configured per platform model; the hard ceiling protects
 * the platform if a model row is accidentally configured with an extreme value.
 */
public final class ChatOutputTokenPolicy {
    public static final int DEFAULT_OUTPUT_TOKENS = 2_048;
    public static final int DEFAULT_MODEL_OUTPUT_CAP = 4_096;
    public static final int HARD_OUTPUT_TOKEN_CAP = 32_768;

    private ChatOutputTokenPolicy() {
    }

    public static int resolve(Integer requested, Integer modelOutputCap, Integer packageOutputCap) {
        int modelCap = positiveOrDefault(modelOutputCap, DEFAULT_MODEL_OUTPUT_CAP);
        int ceiling = Math.min(modelCap, HARD_OUTPUT_TOKEN_CAP);
        if (packageOutputCap != null && packageOutputCap > 0) {
            ceiling = Math.min(ceiling, packageOutputCap);
        }

        int preferred = requested == null || requested <= 0 ? DEFAULT_OUTPUT_TOKENS : requested;
        return Math.max(1, Math.min(preferred, ceiling));
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
