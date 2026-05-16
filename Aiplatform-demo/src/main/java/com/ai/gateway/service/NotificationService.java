package com.ai.gateway.service;

import com.ai.gateway.entity.UserNotification;
import com.ai.gateway.mapper.UserNotificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationMapper notificationMapper;

    /**
     * 发送单条通知
     * @param userId 用户ID
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     * @param relatedId 关联ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification(Long userId, String title, String content, 
                                String type, Long relatedId) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0); // 未读
        notification.setCreateTime(LocalDateTime.now());
        
        notificationMapper.insert(notification);
        
        log.info("通知发送成功: userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * 批量发送通知
     * @param userIds 用户ID列表
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     * @param relatedId 关联ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSendNotification(List<Long> userIds, String title, 
                                     String content, String type, Long relatedId) {
        int successCount = 0;
        for (Long userId : userIds) {
            try {
                sendNotification(userId, title, content, type, relatedId);
                successCount++;
            } catch (Exception e) {
                log.error("发送通知失败: userId={}", userId, e);
            }
        }
        
        log.info("批量通知发送完成: 总数={}, 成功={}", userIds.size(), successCount);
    }

    /**
     * 获取用户未读通知数量
     * @param userId 用户ID
     * @return 未读数量
     */
    public int getUnreadCount(Long userId) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
               .eq(UserNotification::getIsRead, 0);
        
        return Math.toIntExact(notificationMapper.selectCount(wrapper));
    }

    /**
     * 获取用户通知列表（分页）
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @param isRead 是否已读（null表示全部，0表示未读，1表示已读）
     * @return 分页结果
     */
    public Map<String, Object> getNotifications(Long userId, int page, int size, Integer isRead) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId);
        
        if (isRead != null) {
            wrapper.eq(UserNotification::getIsRead, isRead);
        }
        
        wrapper.orderByDesc(UserNotification::getCreateTime);
        
        Page<UserNotification> notificationPage = new Page<>(page, size);
        Page<UserNotification> result = notificationMapper.selectPage(notificationPage, wrapper);
        
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("size", size);
        response.put("unreadCount", getUnreadCount(userId));
        
        return response;
    }

    /**
     * 标记通知为已读
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        UserNotification notification = notificationMapper.selectById(notificationId);
        
        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }
        
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此通知");
        }
        
        if (notification.getIsRead() == 0) {
            notification.setIsRead(1);
            notification.setReadTime(LocalDateTime.now());
            notificationMapper.updateById(notification);
            
            log.info("通知标记为已读: notificationId={}, userId={}", notificationId, userId);
        }
    }

    /**
     * 标记所有通知为已读
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
               .eq(UserNotification::getIsRead, 0);
        
        List<UserNotification> unreadNotifications = notificationMapper.selectList(wrapper);
        
        LocalDateTime now = LocalDateTime.now();
        for (UserNotification notification : unreadNotifications) {
            notification.setIsRead(1);
            notification.setReadTime(now);
            notificationMapper.updateById(notification);
        }
        
        log.info("所有通知标记为已读: userId={}, count={}", userId, unreadNotifications.size());
    }

    /**
     * 删除通知
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotification(Long notificationId, Long userId) {
        UserNotification notification = notificationMapper.selectById(notificationId);
        
        if (notification == null) {
            log.warn("通知不存在: notificationId={}", notificationId);
            return false;
        }
        
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此通知");
        }
        
        notificationMapper.deleteById(notificationId);
        log.info("通知删除成功: notificationId={}, userId={}", notificationId, userId);
        return true;
    }

    /**
     * 获取最新的通知（用于登录时弹窗提醒）
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 最新通知列表
     */
    public List<UserNotification> getLatestNotifications(Long userId, int limit) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
               .eq(UserNotification::getIsRead, 0)
               .orderByDesc(UserNotification::getCreateTime);
        
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Page<UserNotification> page = new Page<>(1, safeLimit);
        return notificationMapper.selectList(page, wrapper);
    }
}
