package com.ai.gateway.config;

import com.ai.gateway.interceptor.IpRateLimitInterceptor;
import com.ai.gateway.interceptor.UserRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final UserRateLimitInterceptor userRateLimitInterceptor;

    /**
     * Knife4j / OpenAPI 文档相关路径，全部拦截器放行，避免文档页被限流误伤
     */
    private static final String[] DOC_PATHS = {
            "/doc.html", "/swagger-ui/**", "/swagger-resources/**",
            "/v3/api-docs/**", "/webjars/**", "/favicon.ico"
    };

    /**
     * 本地文件存储目录（OSS 降级时使用），通过 /uploads/** 静态映射对外提供 HTTP 访问
     */
    @Value("${app.storage.local-path:./uploads}")
    private String localStoragePath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP限流拦截器
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/static/**", "/uploads/**")
                .excludePathPatterns(DOC_PATHS);

        // 用户限流拦截器（防止恶意刷接口）
        registry.addInterceptor(userRateLimitInterceptor)
                // servlet context-path=/api 不参与 MVC 路径匹配，这里应匹配实际的 /chat 路径。
                .addPathPatterns("/chat/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/static/**", "/uploads/**")
                .excludePathPatterns(DOC_PATHS);

        // AI 接口令牌认证统一进入 Spring Security Filter Chain；
        // 这里保留 MVC 层的限流与静态资源配置，避免二次鉴权。
    }

    /**
     * 静态资源映射：将 /uploads/** 映射到本地文件系统目录
     * <p>
     * 当 OSS 降级为本地存储时，文件以 local:// 前缀存入数据库，
     * 前端通过 /api/uploads/路径 访问对应的本地文件（与 OSS URL 一样可直接通过 HTTP 访问）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteLocation = Paths.get(localStoragePath).toAbsolutePath()
                .toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absoluteLocation);
    }
}
