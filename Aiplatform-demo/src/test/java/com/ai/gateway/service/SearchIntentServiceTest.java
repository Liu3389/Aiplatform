package com.ai.gateway.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIntentServiceTest {

    private final SearchIntentService service = new SearchIntentService();

    @Test
    void extractQueryKeepsQuestionMeaning() {
        assertEquals("Spring Boot 3.2 是什么", service.extractSearchQuery("请问 搜索一下 Spring Boot 3.2 是什么？"));
    }

    @Test
    void researchAndFutureYearQuestionsTriggerSearch() {
        assertTrue(service.needsSearch("推荐几个 Java 开源项目"));
        assertTrue(service.needsSearch("2028 年的个人信息保护政策"));
        assertFalse(service.needsSearch("你好"));
        assertFalse(service.needsSearch("今天星期几"));
    }
}
