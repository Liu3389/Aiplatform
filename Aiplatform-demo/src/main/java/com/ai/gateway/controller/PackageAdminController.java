package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.dto.AdminTogglePackageStatusRequest;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.PackageTemplateService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 套餐管理控制器（管理员）
 */
@Slf4j
@RestController
@RequestMapping("/admin/packages")
@RequiredArgsConstructor
public class PackageAdminController {

    private final PackageTemplateService packageTemplateService;
    private final AdminOperationLogService operationLogService; // 修复 CE-1
    private final TokenService tokenService;
    private final UserService userService;

    /**
     * 获取所有套餐列表
     */
    @RequireAdmin
    @GetMapping("/list")
    public Result<List<PackageTemplate>> listPackages(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(required = false) Boolean includeInactive) {
        
        // 修复 P2-11: 移除冗余的手动 isAdmin 检查，统一由 @RequireAdmin 处理
        List<PackageTemplate> packages;
        if (includeInactive != null && includeInactive) {
            packages = packageTemplateService.listAllPackages();
        } else {
            packages = packageTemplateService.listActivePackages();
        }
        
        return Result.success(packages);
    }

    /**
     * 获取套餐详情
     */
    @RequireAdmin
    @GetMapping("/{id}")
    public Result<PackageTemplate> getPackageDetail(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        PackageTemplate pkg = packageTemplateService.getById(id);
        if (pkg == null) {
            return Result.error(404, "套餐不存在");
        }
        
        return Result.success(pkg);
    }

    /**
     * 创建套餐模板
     */
    @RequireAdmin
    @PostMapping("/create")
    public Result<PackageTemplate> createPackage(
            @RequestHeader("X-User-Token") String token,
            @RequestBody PackageTemplate template) {
        
        // 修复 P1-2: 验证套餐价格和点数合理性
        if (template.getPrice() == null || template.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "套餐价格必须大于 0");
        }
        if (template.getPoints() != null && template.getPoints().compareTo(BigDecimal.ZERO) < 0) {
            return Result.error(400, "套餐点数不能为负数");
        }
        
        try {
            PackageTemplate created = packageTemplateService.createPackage(template);
            
            // 修复 P0-2: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "PACKAGE_MANAGE", 
                    "CREATE_PACKAGE", null, java.util.Map.of("packageId", created.getId(), "name", template.getPackageName()));
            
            return Result.success("创建成功", created);
        } catch (Exception e) {
            log.error("创建套餐失败", e);
            
            // 修复 P0-5: 记录失败日志
            try {
                Long adminId = tokenService.getUserIdFromToken(token);
                com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
                String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
                operationLogService.logFailure(adminId, adminUsername, "PACKAGE_MANAGE", 
                        "CREATE_PACKAGE", null, java.util.Map.of("name", template.getPackageName()), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新套餐模板
     */
    @RequireAdmin
    @PutMapping("/update")
    public Result<Void> updatePackage(
            @RequestHeader("X-User-Token") String token,
            @RequestBody PackageTemplate template) {
        
        if (template.getId() == null) {
            return Result.error(400, "套餐ID不能为空");
        }
        
        try {
            packageTemplateService.updatePackage(template);
            
            // 修复 P0-2: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "PACKAGE_MANAGE", 
                    "UPDATE_PACKAGE", null, java.util.Map.of("packageId", template.getId()));
            
            return Result.success("更新成功", null);
        } catch (Exception e) {
            log.error("更新套餐失败", e);
            
            // 修复 P0-5: 记录失败日志
            try {
                Long adminId = tokenService.getUserIdFromToken(token);
                com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
                String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
                operationLogService.logFailure(adminId, adminUsername, "PACKAGE_MANAGE", 
                        "UPDATE_PACKAGE", null, java.util.Map.of("packageId", template.getId()), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 上架/下架套餐
     */
    @RequireAdmin
    @PostMapping("/toggle-status")
    public Result<Void> toggleStatus(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminTogglePackageStatusRequest request) {
        
        Long id = request.getId();
        Integer status = request.getStatus();
        
        try {
            packageTemplateService.togglePackageStatus(id, status);
            
            // 修复 P0-2: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "PACKAGE_MANAGE", 
                    "TOGGLE_STATUS", null, java.util.Map.of("packageId", id, "status", status));
            
            return Result.success(status == 1 ? "上架成功" : "下架成功", null);
        } catch (Exception e) {
            log.error("变更套餐状态失败", e);
            
            // 修复 P1-6: 记录失败日志
            try {
                Long adminId = tokenService.getUserIdFromToken(token);
                com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
                String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
                operationLogService.logFailure(adminId, adminUsername, "PACKAGE_MANAGE", 
                        "TOGGLE_STATUS", null, java.util.Map.of("packageId", id, "status", status), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 删除套餐模板
     */
    @RequireAdmin
    @DeleteMapping("/{id}")
    public Result<Void> deletePackage(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        try {
            packageTemplateService.deletePackage(id);
            
            // 修复 P2-10: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "PACKAGE_MANAGE", 
                    "DELETE_PACKAGE", null, java.util.Map.of("packageId", id));
            
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除套餐失败", e);
            
            // 修复 P1-7: 记录失败日志
            try {
                Long adminId = tokenService.getUserIdFromToken(token);
                com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
                String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
                operationLogService.logFailure(adminId, adminUsername, "PACKAGE_MANAGE", 
                        "DELETE_PACKAGE", null, java.util.Map.of("packageId", id), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }
}
