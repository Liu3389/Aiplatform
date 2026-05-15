package com.ai.gateway.config;

import com.ai.gateway.interceptor.ApiKeyAuthInterceptor;
import com.ai.gateway.interceptor.IpRateLimitInterceptor;
import com.ai.gateway.interceptor.UserRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final UserRateLimitInterceptor userRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP限流拦截器
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/static/**");

        // 用户限流拦截器（防止恶意刷接口）
        registry.addInterceptor(userRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/static/**");

        // API Key认证拦截器（修复 P0: 覆盖所有 AI 交互及导出接口）
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/chat/**", "/v1/ai-doc/**")
                .excludePathPatterns("/auth/**", "/user/**");
    }
}
