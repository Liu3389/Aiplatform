package com.ai.gateway.config;

import com.ai.gateway.interceptor.ApiKeyAuthInterceptor;
import com.ai.gateway.interceptor.IpRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/static/**");

        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/chat/**")
                .excludePathPatterns("/auth/**", "/user/**");
    }
}
