package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.service.PackageTemplateService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
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
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
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
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
        try {
            PackageTemplate created = packageTemplateService.createPackage(template);
            return Result.success("创建成功", created);
        } catch (Exception e) {
            log.error("创建套餐失败", e);
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
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
        if (template.getId() == null) {
            return Result.error(400, "套餐ID不能为空");
        }
        
        try {
            packageTemplateService.updatePackage(template);
            return Result.success("更新成功", null);
        } catch (Exception e) {
            log.error("更新套餐失败", e);
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
            @RequestBody Map<String, Object> body) {
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
        Long id = Long.valueOf(body.get("id").toString());
        Integer status = Integer.valueOf(body.get("status").toString());
        
        try {
            packageTemplateService.togglePackageStatus(id, status);
            return Result.success(status == 1 ? "上架成功" : "下架成功", null);
        } catch (Exception e) {
            log.error("变更套餐状态失败", e);
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
        
        Long adminId = tokenService.getUserIdFromToken(token);
        if (!userService.isAdmin(adminId)) {
            return Result.error(403, "无权限");
        }
        
        try {
            packageTemplateService.deletePackage(id);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除套餐失败", e);
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }
}
