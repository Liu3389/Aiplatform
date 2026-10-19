package com.ai.gateway.init;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyTestDataInitializerTest {
    private static final Pattern API_KEY_FORMAT = Pattern.compile("sk-[0-9a-f]{32}");

    @Test
    void testDataKeysAreFreshAndUnpredictablePerSeededRow() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 1_000).forEach(index -> generated.add(ApiKeyTestDataInitializer.createSeedApiKey()));

        assertEquals(1_000, generated.size());
        assertTrue(generated.stream().allMatch(key -> API_KEY_FORMAT.matcher(key).matches()));
    }
}
