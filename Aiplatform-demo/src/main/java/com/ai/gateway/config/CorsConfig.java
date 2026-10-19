package com.ai.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置
 */
@Slf4j
@Configuration
public class CorsConfig {

    private static final List<String> OWNED_SITE_ORIGINS = List.of(
            "https://lingdian-ai.top",
            "https://www.lingdian-ai.top"
    );

    @Value("${cors.allowed-origins:http://localhost:13000,http://127.0.0.1:13000}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalStateException("CORS allowed origins must be configured explicitly");
        }

        List<String> origins = Arrays.stream(allowedOrigins.split(",", -1))
                .map(String::trim)
                .toList();
        if (origins.stream().anyMatch(origin -> origin.isEmpty() || origin.contains("*"))) {
            // Keep the current application available when an older container still injects '*',
            // but never reflect arbitrary origins while credentials are enabled.
            log.error("CORS wildcard rejected; falling back to the platform's owned HTTPS origins. Update ALLOWED_ORIGINS.");
            origins = OWNED_SITE_ORIGINS;
        }
        for (String origin : origins) {
            config.addAllowedOrigin(origin);
        }
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-User-Token", "X-API-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
