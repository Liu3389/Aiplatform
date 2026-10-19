package com.ai.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {

    @Test
    void credentialsAreAllowedOnlyFromConfiguredOrigins() throws Exception {
        CorsFilter filter = filterFor("https://lingdian-ai.top,https://www.lingdian-ai.top");

        MockHttpServletResponse allowed = preflight(filter, "https://lingdian-ai.top");
        assertEquals("https://lingdian-ai.top", allowed.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));

        MockHttpServletResponse denied = preflight(filter, "https://attacker.example");
        assertNull(denied.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void legacyWildcardFallsBackToOwnedHttpsOriginsInsteadOfReflectingArbitraryOrigin() throws Exception {
        CorsFilter filter = filterFor("*");

        MockHttpServletResponse owned = preflight(filter, "https://www.lingdian-ai.top");
        assertEquals("https://www.lingdian-ai.top", owned.getHeader("Access-Control-Allow-Origin"));

        MockHttpServletResponse attacker = preflight(filter, "https://attacker.example");
        assertNull(attacker.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void embeddedWildcardAlsoFallsBackToOwnedOrigins() throws Exception {
        CorsFilter filter = filterFor("https://lingdian-ai.top,https://*.example");

        MockHttpServletResponse allowed = preflight(filter, "https://lingdian-ai.top");
        assertEquals("https://lingdian-ai.top", allowed.getHeader("Access-Control-Allow-Origin"));

        MockHttpServletResponse denied = preflight(filter, "https://sub.example");
        assertNull(denied.getHeader("Access-Control-Allow-Origin"));
    }

    private CorsFilter filterFor(String origins) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config.corsFilter();
    }

    private MockHttpServletResponse preflight(CorsFilter filter, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login/captcha");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
