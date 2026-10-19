package com.ai.gateway.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totpService = new TotpService("unit-test-key-material", new SecureRandom());

    @Test
    void matchesRfc6238Sha1SixDigitTestVector() {
        String rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", totpService.codeForStep(rfcSecret, 1));
    }

    @Test
    void acceptsOneAdjacentTimeStepAndRejectsOutsideWindow() {
        String secret = totpService.newSecret();
        long currentStep = 100L;
        long nowMillis = currentStep * 30_000L * 1L;

        assertTrue(totpService.matchedStep(secret, totpService.codeForStep(secret, currentStep - 1), nowMillis).isPresent());
        assertTrue(totpService.matchedStep(secret, totpService.codeForStep(secret, currentStep + 1), nowMillis).isPresent());
        assertFalse(totpService.matchedStep(secret, totpService.codeForStep(secret, currentStep - 2), nowMillis).isPresent());
        assertFalse(totpService.matchedStep(secret, "12AB56", nowMillis).isPresent());
    }

    @Test
    void encryptsSecretsWithAuthenticatedRandomNonce() {
        String secret = totpService.newSecret();
        String encrypted = totpService.encryptSecret(42L, secret);

        assertNotEquals(secret, encrypted);
        assertEquals(secret, totpService.decryptSecret(42L, encrypted));
        assertNotEquals(encrypted, totpService.encryptSecret(42L, secret));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> totpService.decryptSecret(43L, encrypted));
    }
}
