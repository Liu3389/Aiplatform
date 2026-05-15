package com.ai.gateway.service;

import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.mapper.PackageTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐模板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageTemplateService {

    private final PackageTemplateMapper packageTemplateMapper;

    /**
     * 获取所有上架的套餐列表
     */
    public List<PackageTemplate> listActivePackages() {
        LambdaQueryWrapper<PackageTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackageTemplate::getStatus, 1)
               .orderByAsc(PackageTemplate::getSortOrder);
        return packageTemplateMapper.selectList(wrapper);
    }

    /**
     * 获取所有套餐（包括下架的）
     */
    public List<PackageTemplate> listAllPackages() {
        LambdaQueryWrapper<PackageTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(PackageTemplate::getSortOrder);
        return packageTemplateMapper.selectList(wrapper);
    }

    /**
     * 根据套餐代码获取套餐
     */
    public PackageTemplate getByCode(String packageCode) {
        LambdaQueryWrapper<PackageTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackageTemplate::getPackageCode, packageCode);
        return packageTemplateMapper.selectOne(wrapper);
    }

    /**
     * 根据ID获取套餐
     */
    public PackageTemplate getById(Long id) {
        return packageTemplateMapper.selectById(id);
    }

    /**
     * 创建套餐模板
     */
    @Transactional(rollbackFor = Exception.class)
    public PackageTemplate createPackage(PackageTemplate template) {
        // 检查套餐代码是否已存在
        PackageTemplate existing = getByCode(template.getPackageCode());
        if (existing != null) {
            throw new RuntimeException("套餐代码已存在: " + template.getPackageCode());
        }
        
        template.setStatus(1); // 默认上架
        template.setCreateTime(java.time.LocalDateTime.now());
        template.setUpdateTime(java.time.LocalDateTime.now());
        packageTemplateMapper.insert(template);
        log.info("创建套餐模板成功: code={}, name={}", template.getPackageCode(), template.getPackageName());
        return template;
    }

    /**
     * 更新套餐模板
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePackage(PackageTemplate template) {
        template.setUpdateTime(java.time.LocalDateTime.now());
        packageTemplateMapper.updateById(template);
        log.info("更新套餐模板成功: id={}, code={}", template.getId(), template.getPackageCode());
    }

    /**
     * 上架/下架套餐
     */
    @Transactional(rollbackFor = Exception.class)
    public void togglePackageStatus(Long id, Integer status) {
        PackageTemplate template = new PackageTemplate();
        template.setId(id);
        template.setStatus(status);
        template.setUpdateTime(java.time.LocalDateTime.now());
        packageTemplateMapper.updateById(template);
        log.info("套餐状态变更: id={}, status={}", id, status == 1 ? "上架" : "下架");
    }

    /**
     * 删除套餐模板
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePackage(Long id) {
        packageTemplateMapper.deleteById(id);
        log.info("删除套餐模板: id={}", id);
    }
}
