package com.ai.gateway.service;

import com.ai.gateway.entity.SystemNotification;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserNotification;
import com.ai.gateway.mapper.SystemNotificationMapper;
import com.ai.gateway.mapper.UserMapper;
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
import java.util.stream.Collectors;

/**
 * 通知统一门面服务（Facade）。
 *
 * 设计目标：将两套通知表在逻辑层透明化，前端只读 user_notification。
 * - 点对点通知（充值到账、点数发放等）：直接写 user_notification
 * - 群发广播（管理员公告）：system_notification 存一条模板（审计），同时为每个目标用户
 *   在 user_notification 写一条投影记录（related_id 指向模板 id），实现已读状态按用户隔离
 *
 * 读取统一走 user_notification，已读/未读/删除均按用户隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacadeService {

    private final UserNotificationMapper userNotificationMapper;
    private final SystemNotificationMapper systemNotificationMapper;
    private final UserMapper userMapper;
    private final NotificationSseManager sseManager;

    /** 单次广播目标用户上限，超过需分批 */
    private static final long BROADCAST_USER_LIMIT = 10000L;

    // ==================== 发送 ====================

    /**
     * 发送点对点通知（直接落 user_notification）。
     *
     * @param userId    目标用户
     * @param title     标题
     * @param content   内容
     * @param type      类型（RECHARGE/POINTS_GRANT/MEMBERSHIP_EXPIRE/COUPON/SYSTEM 等）
     * @param relatedId 关联业务 ID（可空）
     * @param metadata  业务元数据 JSON 字符串（可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void sendToUser(Long userId, String title, String content,
                           String type, Long relatedId, String metadata) {
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setType(type != null ? type : "SYSTEM");
        n.setRelatedId(relatedId);
        n.setMetadata(metadata);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        userNotificationMapper.insert(n);
        // 实时推送到在线用户（SSE），不在线则静默跳过
        sseManager.push(userId, "notification", n);
        log.info("点对点通知已发送: userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * 群发广播：system_notification 存模板 + user_notification 写每个用户的投影。
     *
     * @param adminId  发起管理员 ID（模板 user_id）
     * @param userIds  目标用户 ID 列表
     * @param title    标题
     * @param content  内容
     * @param type     类型
     * @param metadata 业务元数据 JSON 字符串（可空）
     * @return 实际投递条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int sendBroadcast(Long adminId, List<Long> userIds, String title,
                             String content, String type, String metadata) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        // 1. 模板落 system_notification（审计/留痕）
        SystemNotification template = new SystemNotification();
        template.setUserId(adminId != null ? adminId : 0L);
        template.setTitle(title);
        template.setContent(content);
        template.setType(type != null ? type : "system");
        template.setIsRead(false);
        template.setCreateTime(LocalDateTime.now());
        systemNotificationMapper.insert(template);

        // 2. 每个目标用户写一条投影到 user_notification
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Long uid : userIds) {
            UserNotification n = new UserNotification();
            n.setUserId(uid);
            n.setTitle(title);
            n.setContent(content);
            n.setType(type != null ? type : "SYSTEM");
            n.setRelatedId(template.getId());
            n.setMetadata(metadata);
            n.setIsRead(0);
            n.setCreateTime(now);
            userNotificationMapper.insert(n);
            sseManager.push(uid, "notification", n);
            count++;
        }
        log.info("广播通知已投递: adminId={}, templateId={}, recipients={}, title={}",
                adminId, template.getId(), count, title);
        return count;
    }

    /**
     * 群发给全部用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public int sendBroadcastToAll(Long adminId, String title, String content, String type, String metadata) {
        long total = userMapper.selectCount(new LambdaQueryWrapper<>());
        if (total > BROADCAST_USER_LIMIT) {
            throw new RuntimeException("目标用户数量超过" + BROADCAST_USER_LIMIT + "，请分批发送");
        }
        List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<>())
                .stream().map(User::getId).collect(Collectors.toList());
        return sendBroadcast(adminId, userIds, title, content, type, metadata);
    }

    /**
     * 按会员等级群发。
     */
    @Transactional(rollbackFor = Exception.class)
    public int sendBroadcastToMembership(Long adminId, String membership, String title,
                                         String content, String type, String metadata) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getMembership, membership);
        long total = userMapper.selectCount(wrapper);
        if (total > BROADCAST_USER_LIMIT) {
            throw new RuntimeException("目标用户数量超过" + BROADCAST_USER_LIMIT + "，请分批发送");
        }
        List<Long> userIds = userMapper.selectList(wrapper)
                .stream().map(User::getId).collect(Collectors.toList());
        return sendBroadcast(adminId, userIds, title, content, type, metadata);
    }

    /**
     * 按新用户群发（注册 days 天内）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int sendBroadcastToRecentUsers(Long adminId, int days, String title,
                                          String content, String type, String metadata) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, since);
        long total = userMapper.selectCount(wrapper);
        if (total > BROADCAST_USER_LIMIT) {
            throw new RuntimeException("目标用户数量超过" + BROADCAST_USER_LIMIT + "，请分批发送");
        }
        List<Long> userIds = userMapper.selectList(wrapper)
                .stream().map(User::getId).collect(Collectors.toList());
        return sendBroadcast(adminId, userIds, title, content, type, metadata);
    }

    // ==================== 读取（统一走 user_notification） ====================

    /**
     * 查询用户通知列表。
     */
    public List<UserNotification> listByUser(Long userId, boolean unreadOnly) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId);
        if (unreadOnly) wrapper.eq(UserNotification::getIsRead, 0);
        wrapper.orderByDesc(UserNotification::getCreateTime);
        List<UserNotification> result = userNotificationMapper.selectList(wrapper);
        log.info("listByUser: userId={}, unreadOnly={}, resultCount={}", userId, unreadOnly, result.size());
        return result;
    }

    /**
     * 用户未读通知数。
     */
    public int getUnreadCount(Long userId) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
               .eq(UserNotification::getIsRead, 0);
        return userNotificationMapper.selectCount(wrapper).intValue();
    }

    /**
     * 分页查询用户通知（含未读数）。
     */
    public Map<String, Object> getNotifications(Long userId, int page, int size, Integer isRead) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId);
        if (isRead != null) wrapper.eq(UserNotification::getIsRead, isRead);
        wrapper.orderByDesc(UserNotification::getCreateTime);

        Page<UserNotification> result = userNotificationMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("size", size);
        response.put("unreadCount", getUnreadCount(userId));
        return response;
    }

    /**
     * 获取最新未读通知（登录弹窗用）。
     */
    public List<UserNotification> getLatestNotifications(Long userId, int limit) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
               .eq(UserNotification::getIsRead, 0)
               .orderByDesc(UserNotification::getCreateTime)
               .last("LIMIT " + Math.max(1, limit));
        return userNotificationMapper.selectList(wrapper);
    }

    // ==================== 状态管理 ====================

    /**
     * 标记单条通知为已读（校验归属）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        UserNotification n = userNotificationMapper.selectById(notificationId);
        if (n == null) throw new RuntimeException("通知不存在");
        if (!n.getUserId().equals(userId)) throw new RuntimeException("无权操作此通知");
        if (n.getIsRead() == 0) {
            n.setIsRead(1);
            n.setReadTime(LocalDateTime.now());
            userNotificationMapper.updateById(n);
        }
    }

    /**
     * 标记用户所有通知为已读。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId).eq(UserNotification::getIsRead, 0);
        List<UserNotification> unread = userNotificationMapper.selectList(wrapper);
        LocalDateTime now = LocalDateTime.now();
        for (UserNotification n : unread) {
            n.setIsRead(1);
            n.setReadTime(now);
            userNotificationMapper.updateById(n);
        }
        log.info("全部通知标记已读: userId={}, count={}", userId, unread.size());
    }

    /**
     * 删除通知（校验归属）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotification(Long notificationId, Long userId) {
        UserNotification n = userNotificationMapper.selectById(notificationId);
        if (n == null) return false;
        if (!n.getUserId().equals(userId)) throw new RuntimeException("无权删除此通知");
        userNotificationMapper.deleteById(notificationId);
        return true;
    }
}
