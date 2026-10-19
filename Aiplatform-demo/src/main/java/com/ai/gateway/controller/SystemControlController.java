package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.DockerControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 系统控制控制器
 * <p>
 * 提供管理员对 Docker 容器的启停控制能力。
 * 访问需双重验证：① 管理员 Token（@RequireAdmin） ② 二级密码（X-Secondary-Token）
 * <p>
 * 二级密码验证流程：
 * 1. POST /admin/system/verify 传入二级密码
 * 2. 验证通过后生成 secondaryToken 存入 Redis（30 分钟过期）
 * 3. 后续操作请求携带 X-Secondary-Token header
 *
 * @author AI Gateway Platform
 */
@Tag(name = "08-系统控制", description = "Docker 容器启停控制（需管理员权限 + 二级密码）")
@Slf4j
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class SystemControlController {

    private final DockerControlService dockerControlService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.secondary-password:}")
    private String secondaryPassword;

    /** 二级密码 Redis Key 前缀 */
    private static final String SECONDARY_TOKEN_PREFIX = "system:secondary:";
    /** 二级密码 Token 有效期（分钟） */
    private static final long SECONDARY_TOKEN_TTL_MINUTES = 30;
    /** 后端容器短名（不允许 stop） */
    private static final String BACKEND_CONTAINER = "backend";
    /** 环境容器列表（不含 backend） */
    private static final List<String> ENVIRONMENT_CONTAINERS = List.of("mysql", "redis", "frontend");

    // ==================== 二级密码验证 ====================

    @RequireAdmin
    @Operation(summary = "验证二级密码")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verifySecondaryPassword(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, String> body) {

        String password = body.get("password");
        if (password == null || password.isBlank()) {
            throw new BusinessException("请输入二级密码");
        }

        if (secondaryPassword == null || secondaryPassword.isBlank()) {
            return Result.error(503, "系统二级口令未配置，容器控制暂不可用");
        }

        if (!secondaryPassword.equals(password)) {
            log.warn("二级密码验证失败");
            throw new BusinessException("二级密码错误");
        }

        // 生成二级密码 Token
        String secondaryToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                SECONDARY_TOKEN_PREFIX + secondaryToken,
                "verified",
                SECONDARY_TOKEN_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        log.info("二级密码验证通过, 签发 secondaryToken");

        Map<String, Object> result = new HashMap<>();
        result.put("token", secondaryToken);
        result.put("expiresIn", SECONDARY_TOKEN_TTL_MINUTES * 60);
        result.put("dockerAvailable", dockerControlService.isAvailable());
        return Result.success(result);
    }

    // ==================== 容器状态查询 ====================

    @RequireAdmin
    @Operation(summary = "获取所有容器状态")
    @GetMapping("/status")
    public Result<List<Map<String, Object>>> getContainerStatus(
            @RequestHeader("X-User-Token") String token,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        return Result.success(dockerControlService.getAllContainerStatus());
    }

    // ==================== 单容器控制 ====================

    @RequireAdmin
    @Operation(summary = "启动指定容器")
    @PostMapping("/container/{name}/start")
    public Result<String> startContainer(
            @RequestHeader("X-User-Token") String token,
            @PathVariable String name,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        validateContainerName(name);
        log.info("管理员请求启动容器: {}", name);
        dockerControlService.startContainer(name);
        return Result.success("容器 " + name + " 启动成功");
    }

    @RequireAdmin
    @Operation(summary = "停止指定容器")
    @PostMapping("/container/{name}/stop")
    public Result<String> stopContainer(
            @RequestHeader("X-User-Token") String token,
            @PathVariable String name,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        validateContainerName(name);

        // 后端容器不允许停止（停止后无法从界面恢复）
        if (BACKEND_CONTAINER.equals(name)) {
            throw new BusinessException("后端容器不支持停止操作，请使用重启");
        }

        log.info("管理员请求停止容器: {}", name);
        dockerControlService.stopContainer(name);
        return Result.success("容器 " + name + " 停止成功");
    }

    @RequireAdmin
    @Operation(summary = "重启指定容器")
    @PostMapping("/container/{name}/restart")
    public Result<String> restartContainer(
            @RequestHeader("X-User-Token") String token,
            @PathVariable String name,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        validateContainerName(name);
        log.info("管理员请求重启容器: {}", name);
        dockerControlService.restartContainer(name);
        return Result.success("容器 " + name + " 重启成功");
    }

    // ==================== 环境控制 ====================

    @RequireAdmin
    @Operation(summary = "一键启动环境（MySQL+Redis+前端）")
    @PostMapping("/environment/start")
    public Result<String> startEnvironment(
            @RequestHeader("X-User-Token") String token,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        log.info("管理员请求一键启动环境");
        for (String container : ENVIRONMENT_CONTAINERS) {
            try {
                dockerControlService.startContainer(container);
            } catch (Exception e) {
                log.warn("启动容器 {} 失败: {}", container, e.getMessage());
            }
        }
        return Result.success("环境启动完成（MySQL + Redis + 前端）");
    }

    @RequireAdmin
    @Operation(summary = "一键停止环境（MySQL+Redis+前端）")
    @PostMapping("/environment/stop")
    public Result<String> stopEnvironment(
            @RequestHeader("X-User-Token") String token,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        log.info("管理员请求一键停止环境");
        // 逆序停止：先前端，再 Redis，最后 MySQL
        for (int i = ENVIRONMENT_CONTAINERS.size() - 1; i >= 0; i--) {
            String container = ENVIRONMENT_CONTAINERS.get(i);
            try {
                dockerControlService.stopContainer(container);
            } catch (Exception e) {
                log.warn("停止容器 {} 失败: {}", container, e.getMessage());
            }
        }
        return Result.success("环境停止完成（前端 + Redis + MySQL）");
    }

    // ==================== 容器日志 ====================

    @RequireAdmin
    @Operation(summary = "获取容器日志")
    @GetMapping("/logs/{name}")
    public Result<List<String>> getContainerLogs(
            @RequestHeader("X-User-Token") String token,
            @PathVariable String name,
            @RequestParam(defaultValue = "100") int lines,
            HttpServletRequest request) {

        verifySecondaryToken(request);
        validateContainerName(name);
        if (lines < 1 || lines > 1000) {
            lines = 100;
        }
        return Result.success(dockerControlService.getContainerLogs(name, lines));
    }

    // ==================== 私有方法 ====================

    /** 验证二级密码 Token */
    private void verifySecondaryToken(HttpServletRequest request) {
        String secondaryToken = request.getHeader("X-Secondary-Token");
        if (secondaryToken == null || secondaryToken.isBlank()) {
            throw new BusinessException("缺少二级密码验证 Token");
        }
        Boolean exists = redisTemplate.hasKey(SECONDARY_TOKEN_PREFIX + secondaryToken);
        if (Boolean.FALSE.equals(exists)) {
            throw new BusinessException("二级密码验证已过期，请重新验证");
        }
    }

    /** 验证容器名是否在白名单中 */
    private void validateContainerName(String name) {
        if (!DockerControlService.ALLOWED_CONTAINERS.contains(name)) {
            throw new BusinessException("非法容器名: " + name + ", 允许: " + DockerControlService.ALLOWED_CONTAINERS);
        }
    }
}
