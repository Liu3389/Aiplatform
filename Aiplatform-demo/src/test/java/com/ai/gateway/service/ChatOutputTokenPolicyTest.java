package com.ai.gateway.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutputTokenPolicyTest {
    @Test
    void omissionAndNonPositiveValuesUseBoundedDefault() {
        assertEquals(2_048, ChatOutputTokenPolicy.resolve(null, 8_192, 6_000));
        assertEquals(2_048, ChatOutputTokenPolicy.resolve(-9, 8_192, 6_000));
        assertEquals(1_536, ChatOutputTokenPolicy.resolve(null, 1_536, 6_000));
    }

    @Test
    void requestedTokensAreClampedByModelPackageAndPlatformCeilings() {
        assertEquals(512, ChatOutputTokenPolicy.resolve(9_000, 8_192, 512));
        assertEquals(768, ChatOutputTokenPolicy.resolve(768, 8_192, 12_000));
        assertEquals(32_768, ChatOutputTokenPolicy.resolve(50_000, 50_000, null));
    }

    @Test
    void missingOrInvalidModelCapabilityFallsBackToSafeCap() {
        assertEquals(4_096, ChatOutputTokenPolicy.resolve(50_000, null, null));
        assertEquals(4_096, ChatOutputTokenPolicy.resolve(50_000, 0, 0));
    }
}
