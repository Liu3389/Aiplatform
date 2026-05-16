package com.ai.gateway.service;

import com.ai.gateway.entity.SystemNotification;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.SystemNotificationMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final SystemNotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public List<SystemNotification> listByUser(Long userId, boolean unreadOnly) {
        LambdaQueryWrapper<SystemNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemNotification::getUserId, userId);
        if (unreadOnly) wrapper.eq(SystemNotification::getIsRead, false);
        wrapper.orderByDesc(SystemNotification::getCreateTime);
        return notificationMapper.selectList(wrapper);
    }

    public int getUnreadCount(Long userId) {
        LambdaQueryWrapper<SystemNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemNotification::getUserId, userId)
               .eq(SystemNotification::getIsRead, false);
        return notificationMapper.selectCount(wrapper).intValue();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id, Long userId) {
        SystemNotification n = notificationMapper.selectById(id);
        if (n != null && n.getUserId().equals(userId)) {
            n.setIsRead(true);
            notificationMapper.updateById(n);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<SystemNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemNotification::getUserId, userId)
               .eq(SystemNotification::getIsRead, false);
        List<SystemNotification> unread = notificationMapper.selectList(wrapper);
        for (SystemNotification n : unread) {
            n.setIsRead(true);
            notificationMapper.updateById(n);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void sendToUser(Long userId, String title, String content, String type) {
        SystemNotification n = new SystemNotification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setType(type != null ? type : "system");
        n.setIsRead(false);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
        log.info("通知已发送: userId={}, title={}", userId, title);
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToAllUsers(String title, String content, String type) {
        Long totalCount = userMapper.selectCount(new LambdaQueryWrapper<>());
        if (totalCount > 10000) {
            log.warn("目标用户数量过大({}), 建议分批发送", totalCount);
            throw new RuntimeException("目标用户数量超过10000，请分批发送");
        }
        
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        int count = 0;
        for (User u : users) {
            sendToUser(u.getId(), title, content, type);
            count++;
        }
        log.info("群发通知完成: count={}, title={}", count, title);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToMembership(String membership, String title, String content, String type) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getMembership, membership);
        Long totalCount = userMapper.selectCount(wrapper);
        if (totalCount > 10000) {
            log.warn("目标用户数量过大({}), 建议分批发送", totalCount);
            throw new RuntimeException("目标用户数量超过10000，请分批发送");
        }
        
        List<User> users = userMapper.selectList(wrapper);
        int count = 0;
        for (User u : users) {
            sendToUser(u.getId(), title, content, type);
            count++;
        }
        log.info("按会员发通知完成: membership={}, count={}, title={}", membership, count, title);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToRecentUsers(int days, String title, String content, String type) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, since);
        List<User> users = userMapper.selectList(wrapper);
        int count = 0;
        for (User u : users) {
            sendToUser(u.getId(), title, content, type);
            count++;
        }
        log.info("按新用户发通知完成: days={}, count={}, title={}", days, count, title);
        return count;
    }
}
