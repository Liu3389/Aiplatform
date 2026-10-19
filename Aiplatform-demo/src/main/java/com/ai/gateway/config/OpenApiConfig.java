package com.ai.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 文档配置
 * <ul>
 *   <li>全局文档信息（标题/版本/描述）</li>
 *   <li>全局鉴权方案：X-User-Token（Knife4j 页面右上角 Authorize 填一次，所有接口自动携带）</li>
 *   <li>按业务域分组，文档左侧树形菜单更清晰</li>
 * </ul>
 *
 * @author AI Gateway Platform
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("凌点AI平台 API 文档")
                        .version("1.0.0")
                        .description("AI模型调度网关与流式对话平台接口文档"))
                .components(new Components().addSecuritySchemes("X-User-Token",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Token")
                                .description("登录接口(/auth/login)返回的 token，Authorize 填写后所有接口自动携带该请求头")))
                .addSecurityItem(new SecurityRequirement().addList("X-User-Token"));
    }

    /** 认证中心：注册/登录/Token/第三方OAuth */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("01-认证中心").pathsToMatch("/auth/**").build();
    }

    /** 用户中心：用户信息/会话/API Key/订阅/优惠券/通知 */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder().group("02-用户中心")
                .pathsToMatch("/user/**", "/api-key/**").build();
    }

    /** 对话中心：流式/非流式对话、导出 */
    @Bean
    public GroupedOpenApi chatApi() {
        return GroupedOpenApi.builder().group("03-对话中心").pathsToMatch("/chat/**").build();
    }

    /** AI 文档处理 */
    @Bean
    public GroupedOpenApi aiDocApi() {
        return GroupedOpenApi.builder().group("04-AI文档").pathsToMatch("/v1/ai-doc/**").build();
    }

    /** 文件服务：上传/解析 */
    @Bean
    public GroupedOpenApi fileApi() {
        return GroupedOpenApi.builder().group("05-文件服务").pathsToMatch("/v1/file/**").build();
    }

    /** 模型市场：套餐/模型 */
    @Bean
    public GroupedOpenApi marketApi() {
        return GroupedOpenApi.builder().group("06-模型市场")
                .pathsToMatch("/marketplace/**", "/platform-model/**").build();
    }

    /** 管理后台：管理员/套餐管理/测试数据 */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("07-管理后台").pathsToMatch("/admin/**").build();
    }
}
