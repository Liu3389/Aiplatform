package com.ai.gateway.aspect;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理员权限验证切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuthAspect {

    private final TokenService tokenService;
    private final UserService userService;

    @Around("@annotation(requireAdmin)")
    public Object checkAdminPermission(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw new BusinessException("无法获取请求上下文");
            }
            
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("X-User-Token");
            
            if (token == null || token.isEmpty()) {
                throw new BusinessException("缺少认证Token");
            }
            
            Long userId;
            try {
                userId = tokenService.getUserIdFromToken(token);
            } catch (Exception e) {
                log.error("Token验证异常: path={}, error={}", request.getRequestURI(), e.getMessage());
                throw new BusinessException("Token验证失败，请重新登录");
            }
            
            if (userId == null) {
                throw new BusinessException("Token无效或已过期");
            }
            
            User user = userService.getUserEntityById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            
            String role = user.getRole();
            if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                log.warn("权限不足: userId={}, role={}, path={}", userId, role, request.getRequestURI());
                throw new BusinessException(ResultCode.FORBIDDEN, "权限不足，需要管理员角色");
            }
            
            log.debug("管理员权限验证通过: userId={}, role={}", userId, role);
            
            return joinPoint.proceed();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("管理员权限验证异常: error={}", e.getMessage(), e);
            throw new BusinessException("权限验证失败: " + e.getMessage());
        }
    }
}
