package com.ai.gateway.service;

import com.ai.gateway.common.ResultCode;
import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserContactVerification;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.exception.TwoFactorRequiredException;
import com.ai.gateway.mapper.BillingRecordMapper;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.UserContactVerificationMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class UserService {

    private final UserMapper userMapper;
    private final BillingRecordMapper billingRecordMapper;
    private final PointsBillMapper pointsBillMapper;
    private final CouponService couponService;
    private final PointsService pointsService;
    private final BillingService billingService;
    private final UserSubscriptionService userSubscriptionService;
    private final UserContactVerificationMapper contactVerificationMapper;
    private final VerificationCodeDeliveryService verificationCodeDeliveryService;
    private final StringRedisTemplate redisTemplate;
    private final SessionService sessionService;
    private final UserTwoFactorService twoFactorService;
    private final ConversationVersionService conversationVersionService;
    private final com.ai.gateway.mapper.ConversationMapper conversationMapper;
    private final com.ai.gateway.mapper.ChatMessageMapper chatMessageMapper;
    private final com.ai.gateway.mapper.PlatformStatisticsMapper platformStatisticsMapper;
    private final com.ai.gateway.mapper.CallLogMapper callLogMapper;
    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;
    // 注入配置了 LocalDateTime 格式的 ObjectMapper（替代裸 new ObjectMapper()）
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String BIND_CODE_PREFIX = "user:contact:bind:code:v2:";
    private static final String BIND_ATTEMPT_PREFIX = "user:contact:bind:attempt:v2:";
    private static final String BIND_COOLDOWN_PREFIX = "user:contact:bind:cooldown:v2:";
    private static final String LOGIN_DEVICE_PREFIX = "login:device:";
    private static final int BIND_CODE_TTL_SECONDS = 10 * 60;
    private static final int BIND_COOLDOWN_SECONDS = 60;
    private static final int BIND_CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> ACQUIRE_BIND_COOLDOWN = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY_BIND_CODE = new DefaultRedisScript<>("""
            local saved = redis.call('GET', KEYS[1])
            if not saved then return 0 end
            if saved == ARGV[1] then
              redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
              return 1
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end
            if attempts >= tonumber(ARGV[3]) then
              redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
              return -1
            end
            return -2
            """, Long.class);

    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(UserRegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User existingUser = userMapper.selectOne(wrapper);

        if (existingUser != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户名已存在");
        }
                
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Email is a verified contact, so signup only records it after the bind-code flow succeeds.
        user.setBalance(BigDecimal.ZERO);
        user.setPoints(BigDecimal.ZERO); // 修复 P0-1: 初始化点数
        user.setStatus(1);
        user.setRole("USER");
        user.setFreeApiStrategy("NONE");
        user.setFreeQuota(0);
        user.setDailyCallLimit(0);
        user.setMonthlyCallLimit(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);

        billingService.syncUserBalanceToRedis(user.getId(), user.getBalance());

        log.info("用户注册成功: username={}, userId={}", request.getUsername(), user.getId());

        return convertToVO(user);
    }

    public UserInfoVO login(UserLoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }

        boolean passwordMatch;
        if (user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$") || user.getPassword().startsWith("$2y$")) {
            passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } else {
            String encryptedPassword = cn.hutool.crypto.SecureUtil.md5(request.getPassword());
            passwordMatch = encryptedPassword.equals(user.getPassword());
            if (passwordMatch) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(user);
                log.info("密码自动升级为BCrypt: userId={}", user.getId());
            }
        }

        if (!passwordMatch) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if (twoFactorService.isEnabled(user.getId())) {
            String code = request.getTwoFactorCode();
            if (code == null || code.isBlank()) throw new TwoFactorRequiredException();
            if (!twoFactorService.verifyAndConsumeLoginCode(user.getId(), code)) {
                throw new BusinessException(ResultCode.LOGIN_ERROR);
            }
        }

        // 登录时同步余额到Redis，确保计费系统可以正常使用
        billingService.syncUserBalanceToRedis(user.getId(), user.getBalance());

        log.info("用户登录成功: username={}, userId={}", request.getUsername(), user.getId());

        return convertToVO(user);
    }

    /**
     * 创建用户会话
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @param token 用户Token
     */
    public void createSession(Long userId, String deviceId, String token) {
        sessionService.createSession(userId, deviceId, token);
    }

    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        return convertToVO(user);
    }

    public User getUserEntityById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    public boolean isAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && ("ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole()));
    }

    public BigDecimal getBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        return user.getBalance();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBalance(Long userId, BigDecimal newBalance) {
        User user = new User();
        user.setId(userId);
        user.setBalance(newBalance);
        userMapper.updateById(user);

        log.debug("更新用户余额: userId={}, newBalance={}", userId, newBalance);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("密码修改成功: userId={}", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("管理员重置密码: userId={}", userId);
    }

    public List<User> listAllUsers() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectList(wrapper);
    }

    public long countUsers() {
        // 修复 P1-1: 使用缓存计数器替代全表COUNT(*)查询
        try {
            // 从缓存获取近似计数
            String cacheKey = "user:count:approx";
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Long.parseLong(cached);
            }

            // 缓存未命中，查询数据库并缓存
            long count = userMapper.selectCount(null);
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(count), 30, TimeUnit.MINUTES);
            return count;
        } catch (Exception e) {
            log.warn("获取用户总数失败，返回0", e);
            return 0;
        }
    }

    public long countActiveUsers() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1);
        return userMapper.selectCount(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleUserStatus(Long userId, String status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setStatus("ACTIVE".equals(status) ? 1 : 0);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        if (user.getStatus() == 0) sessionService.revokeAllUserSessions(userId, false);

        log.info("用户状态更新: userId={}, status={}", userId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setRole(role);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("角色分配: userId={}, role={}", userId, role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setFreeApiStrategy(Long userId, String strategy, Integer quota,
                                   Integer dailyLimit, Integer monthlyLimit) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setFreeApiStrategy(strategy);
        user.setFreeQuota(quota != null ? quota : 0);
        user.setDailyCallLimit(dailyLimit != null ? dailyLimit : 0);
        user.setMonthlyCallLimit(monthlyLimit != null ? monthlyLimit : 0);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("免费策略设置: userId={}, strategy={}", userId, strategy);
    }

    public void sendBindCode(Long userId, String type, String target) {
        if (userId == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        String normalizedType = normalizeContactType(type);
        String normalizedTarget = normalizeContactTarget(normalizedType, target);
        ensureContactAvailable(userId, normalizedType, normalizedTarget);

        String userCooldownKey = BIND_COOLDOWN_PREFIX + "user:" + userId + ":" + normalizedType;
        String targetCooldownKey = bindTargetCooldownKey(normalizedType, normalizedTarget);
        Long acquired = redisTemplate.execute(ACQUIRE_BIND_COOLDOWN,
                List.of(userCooldownKey, targetCooldownKey), Integer.toString(BIND_COOLDOWN_SECONDS));
        if (!Long.valueOf(1L).equals(acquired)) {
            throw new BusinessException(ResultCode.RATE_LIMIT_EXCEEDED.getCode(), "验证码发送过于频繁，请稍后重试");
        }

        String codeKey = bindCodeKey(userId, normalizedType, normalizedTarget);
        String attemptKey = bindAttemptKey(userId, normalizedType, normalizedTarget);
        String localInboxKey = VerificationCodeDeliveryService.localInboxKey(userId, normalizedType, normalizedTarget);
        redisTemplate.delete(List.of(codeKey, attemptKey));
        verificationCodeDeliveryService.clearLocalCode(userId, normalizedType, normalizedTarget);
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        String expected = userId + ":" + sha256(userId + ":" + normalizedType + ":" + normalizedTarget + ":" + code);
        redisTemplate.opsForValue().set(codeKey, expected, BIND_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        try {
            verificationCodeDeliveryService.deliver(userId, normalizedType, normalizedTarget, code);
        } catch (RuntimeException e) {
            redisTemplate.delete(List.of(codeKey, attemptKey, userCooldownKey, targetCooldownKey, localInboxKey));
            throw e;
        }
        log.info("绑定验证码已投递: userId={}, type={}", userId, normalizedType);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(Long userId, String email, String code) {
        bindVerifiedContact(userId, "email", email, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(Long userId, String phone, String code) {
        bindVerifiedContact(userId, "phone", phone, code);
    }

    private void bindVerifiedContact(Long userId, String type, String target, String code) {
        String normalizedTarget = normalizeContactTarget(type, target);
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "验证码错误或已过期");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        ensureContactAvailable(userId, type, normalizedTarget);

        String codeKey = bindCodeKey(userId, type, normalizedTarget);
        String attemptKey = bindAttemptKey(userId, type, normalizedTarget);
        String localInboxKey = VerificationCodeDeliveryService.localInboxKey(userId, type, normalizedTarget);
        String expected = userId + ":" + sha256(userId + ":" + type + ":" + normalizedTarget + ":" + code);
        Long verified = redisTemplate.execute(VERIFY_BIND_CODE,
                List.of(codeKey, attemptKey, localInboxKey), expected,
                Integer.toString(BIND_CODE_TTL_SECONDS), Integer.toString(BIND_CODE_MAX_ATTEMPTS));
        if (Long.valueOf(1L).equals(verified)) {
            claimVerifiedContact(user, type, normalizedTarget);
            return;
        }
        if (Long.valueOf(-1L).equals(verified)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "验证码错误次数过多，请重新获取");
        }
        throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "验证码错误或已过期");
    }

    /**
     * Records an email already verified by a trusted OIDC provider.
     * The local account still uses the same uniqueness checks and persistence path
     * as an email verified through the platform's own binding flow.
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindOAuthVerifiedEmail(Long userId, String verifiedEmail) {
        if (userId == null || verifiedEmail == null || verifiedEmail.isBlank()) return;
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        String normalizedEmail = normalizeContactTarget("email", verifiedEmail);
        UserContactVerification existing = contactVerificationMapper.selectById(userId);
        if (existing != null && normalizedEmail.equals(existing.getEmail())) {
            if (!normalizedEmail.equals(user.getEmail())) {
                claimVerifiedContact(user, "email", normalizedEmail);
            }
            return;
        }

        ensureContactAvailable(userId, "email", normalizedEmail);
        claimVerifiedContact(user, "email", normalizedEmail);
    }

    private void ensureContactAvailable(Long userId, String type, String target) {
        LambdaQueryWrapper<UserContactVerification> verifiedQuery = new LambdaQueryWrapper<>();
        if ("email".equals(type)) verifiedQuery.eq(UserContactVerification::getEmail, target);
        else verifiedQuery.eq(UserContactVerification::getPhone, target);
        UserContactVerification verifiedContact = contactVerificationMapper.selectOne(verifiedQuery.last("LIMIT 1"));
        if (verifiedContact != null) {
            String message = "email".equals(type) ? "该邮箱已被绑定" : "该手机号已被绑定";
            throw new BusinessException(409, message);
        }

        LambdaQueryWrapper<User> profileQuery = new LambdaQueryWrapper<>();
        if ("email".equals(type)) profileQuery.eq(User::getEmail, target);
        else profileQuery.eq(User::getPhone, target);
        profileQuery.ne(User::getId, userId);
        if (userMapper.selectCount(profileQuery) > 0) {
            String message = "email".equals(type) ? "该邮箱已关联其他账号，请先完成账号资料核验" : "该手机号已关联其他账号，请先完成账号资料核验";
            throw new BusinessException(409, message);
        }
    }

    private void claimVerifiedContact(User user, String type, String target) {
        UserContactVerification contact = contactVerificationMapper.selectById(user.getId());
        boolean insert = contact == null;
        if (insert) {
            contact = new UserContactVerification();
            contact.setUserId(user.getId());
        }
        if ("email".equals(type)) {
            contact.setEmail(target);
            user.setEmail(target);
        } else {
            contact.setPhone(target);
            user.setPhone(target);
        }
        LocalDateTime now = LocalDateTime.now();
        contact.setUpdatedAt(now);
        user.setUpdateTime(now);
        try {
            if (insert) contactVerificationMapper.insert(contact);
            else contactVerificationMapper.updateById(contact);
            if (userMapper.updateById(user) != 1) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            String message = "email".equals(type) ? "该邮箱已被绑定" : "该手机号已被绑定";
            throw new BusinessException(409, message);
        }
        log.info("已验证联系方式绑定成功: userId={}, type={}", user.getId(), type);
    }

    private String normalizeContactType(String type) {
        if (type == null) throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "绑定类型不正确");
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!"email".equals(normalized) && !"phone".equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "绑定类型不正确");
        }
        return normalized;
    }

    private String normalizeContactTarget(String type, String target) {
        if (target == null) throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "联系方式不能为空");
        String normalized = target.trim();
        if ("email".equals(type)) {
            normalized = normalized.toLowerCase(Locale.ROOT);
            if (normalized.length() > 128 || !normalized.matches("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+$")) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "邮箱格式不正确");
            }
        } else if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "手机号格式不正确");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String bindCodeKey(Long userId, String type, String target) {
        return BIND_CODE_PREFIX + userId + ":" + type + ":" + hashForRedis(target);
    }

    static String bindTargetCooldownKey(String type, String target) {
        return BIND_COOLDOWN_PREFIX + "target:" + type + ":" + hashForRedis(target);
    }

    static String bindUserCooldownKey(Long userId, String type) {
        return BIND_COOLDOWN_PREFIX + "user:" + userId + ":" + type;
    }

    private static String bindAttemptKey(Long userId, String type, String target) {
        return BIND_ATTEMPT_PREFIX + userId + ":" + type + ":" + hashForRedis(target);
    }

    private static String hashForRedis(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Map<String, String> enable2FA(Long userId) {
        return twoFactorService.beginEnrollment(userId);
    }

    public void disable2FA(Long userId, String code) {
        twoFactorService.disable(userId, code);
    }

    public Map<String, Object> verify2FA(Long userId, String code) {
        return twoFactorService.confirmEnrollment(userId, code);
    }

    public Map<String, Object> get2FAStatus(Long userId) {
        return twoFactorService.status(userId);
    }

    public List<Map<String, Object>> getLoginDevices(Long userId) {
        return sessionService.listActiveDevices(userId);
    }

    public void removeLoginDevice(Long userId, String deviceId) {
        String key = LOGIN_DEVICE_PREFIX + userId + ":" + deviceId;
        redisTemplate.delete(key);
        sessionService.revokeDevice(userId, deviceId);
        log.info("移除登录设备: userId={}, deviceId={}", userId, deviceId);
    }

    public void removeAllLoginDevices(Long userId, boolean exceptCurrent) {
        String pattern = LOGIN_DEVICE_PREFIX + userId + ":*";
        Set<String> keys = redisScanUtil.scanKeys(pattern);
        if (exceptCurrent) {
            keys.removeIf(k -> k.endsWith(":current"));
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        sessionService.revokeAllUserSessions(userId, exceptCurrent);
        log.info("移除所有登录设备: userId={}, exceptCurrent={}", userId, exceptCurrent);
    }

    public void revokeUserSessions(Long userId) {
        sessionService.revokeAllUserSessions(userId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, String password) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "密码错误");
        }

        user.setStatus(0);
        user.setUsername("[DELETED]" + user.getUsername() + "_" + user.getId());
        user.setEmail(null);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        sessionService.revokeAllUserSessions(userId, false);

        log.info("账户已注销: userId={}", userId);
    }

    public Map<String, Object> getRechargeRecords(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);

        List<Map<String, Object>> all = new ArrayList<>();

        // 1. 余额充值记录（billing_record type=2）
        LambdaQueryWrapper<com.ai.gateway.entity.BillingRecord> brWrapper = new LambdaQueryWrapper<>();
        brWrapper.eq(com.ai.gateway.entity.BillingRecord::getUserId, userId)
                .eq(com.ai.gateway.entity.BillingRecord::getType, 2)
                .orderByDesc(com.ai.gateway.entity.BillingRecord::getCreateTime);
        for (com.ai.gateway.entity.BillingRecord r : billingRecordMapper.selectList(brWrapper)) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", "B" + r.getId());
            record.put("amount", r.getAmount());
            record.put("changeType", "BALANCE_RECHARGE");
            record.put("balanceBefore", r.getBalanceBefore());
            record.put("balanceAfter", r.getBalanceAfter());
            record.put("status", "COMPLETED");
            record.put("description", "余额充值 ¥" + r.getAmount());
            record.put("businessId", null);
            record.put("createTime", r.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            all.add(record);
        }

        // 2. 点数充值/会员发放记录（points_bill）
        LambdaQueryWrapper<PointsBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, Arrays.asList("RECHARGE", "SUBSCRIPTION_GRANT"))
                .orderByDesc(PointsBill::getCreateTime);
        for (PointsBill bill : pointsBillMapper.selectList(wrapper)) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", "P" + bill.getId());
            record.put("points", bill.getPointsChange());
            record.put("changeType", bill.getChangeType());
            record.put("balanceBefore", bill.getBalanceBefore());
            record.put("balanceAfter", bill.getBalanceAfter());
            record.put("status", "COMPLETED");
            record.put("description", bill.getDescription());
            record.put("businessId", bill.getBusinessId());
            record.put("createTime", bill.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            all.add(record);
        }

        // 按时间倒序合并后分页
        all.sort((a, b) -> b.get("createTime").toString().compareTo(a.get("createTime").toString()));
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<Map<String, Object>> records = new ArrayList<>(all.subList(from, to));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", all.size());
        result.put("page", safePage);
        result.put("size", safeSize);

        log.info("查询充值记录: userId={}, page={}, size={}, count={}, total={}", userId, page, size, records.size(), all.size());
        return result;
    }

    /**
     * 获取用户完整的充值/购买历史记录（包含充值、订阅购买）
     */
    public Map<String, Object> getRechargeHistory(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);

        // 1. 查询点数账单中的充值记录
        Page<PointsBill> billPageObj = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<PointsBill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, Arrays.asList("RECHARGE", "SUBSCRIPTION_GRANT"))
                .orderByDesc(PointsBill::getCreateTime);
        Page<PointsBill> billPage = pointsBillMapper.selectPage(billPageObj, billWrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PointsBill bill : billPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", bill.getId());
            item.put("recordType", "POINTS_BILL");
            item.put("changeType", bill.getChangeType());
            item.put("points", bill.getPointsChange().abs());
            item.put("description", bill.getDescription());
            item.put("businessId", bill.getBusinessId());
            item.put("createTime", bill.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            items.add(item);
        }

        // 2. 查询订阅购买记录
        LambdaQueryWrapper<UserSubscription> subWrapper = new LambdaQueryWrapper<>();
        subWrapper.eq(UserSubscription::getUserId, userId)
                .orderByDesc(UserSubscription::getCreateTime);
        List<UserSubscription> subscriptions = userSubscriptionService.getSubscriptionHistory(userId);

        for (UserSubscription sub : subscriptions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", sub.getId());
            item.put("recordType", "SUBSCRIPTION");
            item.put("changeType", "PURCHASE");
            item.put("amount", sub.getPricePaid());
            item.put("packageName", sub.getPackageName());
            item.put("identityLabel", sub.getIdentityLabel());
            item.put("pointsGranted", sub.getPointsGranted());
            item.put("orderId", sub.getOrderId());
            item.put("startTime", sub.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            item.put("expireTime", sub.getExpireTime() != null ? sub.getExpireTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            item.put("status", sub.getStatus());
            item.put("createTime", sub.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            items.add(item);
        }

        // 按时间排序
        items.sort((a, b) -> {
            String timeA = (String) a.get("createTime");
            String timeB = (String) b.get("createTime");
            return timeB.compareTo(timeA);
        });

        // 分页处理
        int total = items.size();
        int fromIndex = (safePage - 1) * safeSize;
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<Map<String, Object>> pagedItems;
        if (fromIndex >= total) {
            pagedItems = new ArrayList<>();
        } else {
            pagedItems = items.subList(fromIndex, toIndex);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", pagedItems);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);

        log.info("查询完整充值/购买历史: userId={}, page={}, size={}, total={}", userId, page, size, total);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyInvoice(Long userId, Map<String, Object> invoiceData) {
        String invoiceTitle = (String) invoiceData.get("title");
        String taxNumber = (String) invoiceData.get("taxNumber");
        BigDecimal amount = new BigDecimal(invoiceData.getOrDefault("amount", "0").toString());

        if (invoiceTitle == null || invoiceTitle.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "发票抬头不能为空");
        }

        log.info("发票申请已提交: userId={}, title={}, taxNumber={}, amount={}", userId, invoiceTitle, taxNumber, amount);
    }

    // ========== 对话历史云端同步 ==========

    private static final String CONVERSATIONS_KEY = "conversations:";
    private static final String CONVERSATIONS_VERSION_KEY = "conversations:version:";

    public long syncConversations(Long userId, List<Map<String, Object>> conversations) {
        if (conversations == null) conversations = new ArrayList<>();

        // 修复 P0-2: 异步生成会话标题
        for (Map<String, Object> conv : conversations) {
            String title = (String) conv.get("title");
            if (title == null || title.trim().isEmpty() || "无标题会话".equals(title)) {
                Object idObj = conv.get("id");
                Long convId = null;
                if (idObj instanceof Number) {
                    convId = ((Number) idObj).longValue();
                } else if (idObj instanceof String) {
                    try {
                        convId = Long.parseLong((String) idObj);
                    } catch (NumberFormatException ignored) {
                        log.debug("非数字会话ID，跳过标题生成: {}", idObj);
                    }
                }
                if (convId != null) {
                    generateTitleAsync(userId, convId);
                }
            }
        }

        String dataKey = CONVERSATIONS_KEY + userId;
        String versionKey = CONVERSATIONS_VERSION_KEY + userId;
        String lockKey = "lock:conv:sync:" + userId;

        // 加分布式锁防止并发覆盖
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("获取同步锁失败，直接写入: userId={}", userId);
        }

        try {
            // ===== 修复多设备数据丢失：改为 merge 模式，不再全量覆盖 =====
            // 1. 读取 Redis 现有会话列表
            List<Map<String, Object>> existing = new ArrayList<>();
            String existingJson = redisTemplate.opsForValue().get(dataKey);
            if (existingJson != null && !existingJson.isEmpty()) {
                try {
                    existing = objectMapper.readValue(existingJson, List.class);
                } catch (Exception e) {
                    log.warn("反序列化现有会话失败，将从DB加载: userId={}", userId);
                    existing = loadConversationsFromDatabase(userId);
                }
            } else {
                // Redis 为空，从 DB 加载
                existing = loadConversationsFromDatabase(userId);
            }

            // 2. 按 id 建 Map（统一转为 String 比较）
            Map<String, Map<String, Object>> mergedMap = new LinkedHashMap<>();
            for (Map<String, Object> conv : existing) {
                String id = String.valueOf(conv.get("id"));
                mergedMap.put(id, conv);
            }

            // 3. 遍历入参，按 updatedAt 合并（保留最新版本）
            for (Map<String, Object> incoming : conversations) {
                String id = String.valueOf(incoming.get("id"));
                if (id == null || "null".equals(id)) continue;

                Map<String, Object> existingConv = mergedMap.get(id);
                if (existingConv == null) {
                    // 新会话，直接加入
                    mergedMap.put(id, incoming);
                } else {
                    // 已存在，比较 updatedAt 保留最新
                    long existingUpdated = parseTimestamp(existingConv.get("updatedAt"));
                    long incomingUpdated = parseTimestamp(incoming.get("updatedAt"));
                    if (incomingUpdated >= existingUpdated) {
                        mergedMap.put(id, incoming);
                    }
                    // 否则保留现有的
                }
            }

            // 4. 序列化合并后的列表
            List<Map<String, Object>> mergedList = new ArrayList<>(mergedMap.values());
            String json;
            try {
                json = objectMapper.writeValueAsString(mergedList);
            } catch (Exception e) {
                log.error("序列化合并后的对话失败", e);
                return 0;
            }

            long version = System.currentTimeMillis();
            redisTemplate.opsForValue().set(dataKey, json, 90, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(versionKey, String.valueOf(version), 90, TimeUnit.DAYS);
            log.info("对话同步成功(merge模式): userId={}, incoming={}, existing={}, merged={}, version={}",
                    userId, conversations.size(), existing.size(), mergedList.size(), version);

            // 5. 异步写入DB（best-effort，失败仅记日志）
            final List<Map<String, Object>> conversationsFinal = conversations;
            CompletableFuture.runAsync(() -> {
                try {
                    syncConversationsToDatabase(userId, conversationsFinal);
                } catch (Exception e) {
                    log.warn("异步写入会话到DB失败: userId={}", userId, e);
                }
            });

            return version;

        } finally {
            if (Boolean.TRUE.equals(locked)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    /** 解析时间戳字段，兼容 Long、String、Number */
    private long parseTimestamp(Object ts) {
        if (ts == null) return 0;
        if (ts instanceof Number) return ((Number) ts).longValue();
        try {
            return Long.parseLong(String.valueOf(ts));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 异步将会话列表写入数据库（upsert） */
    private void syncConversationsToDatabase(Long userId, List<Map<String, Object>> conversations) {
        for (Map<String, Object> conv : conversations) {
            try {
                Object idObj = conv.get("id");
                if (idObj == null) continue;
                Long convId = null;
                if (idObj instanceof Number) {
                    convId = ((Number) idObj).longValue();
                } else {
                    try { convId = Long.parseLong(String.valueOf(idObj)); } catch (Exception ignored) { continue; }
                }
                if (convId == null) continue;

                // 检查会话是否已存在（包括已逻辑删除的）
                com.ai.gateway.entity.Conversation existing = conversationMapper.selectById(convId);
                if (existing == null) {
                    // 新建会话记录
                    com.ai.gateway.entity.Conversation entity = new com.ai.gateway.entity.Conversation();
                    entity.setId(convId);
                    entity.setUserId(userId);
                    entity.setTitle((String) conv.getOrDefault("title", "新对话"));
                    entity.setModel((String) conv.getOrDefault("model", "deepseek-v4-flash"));
                    entity.setMessageCount(conv.get("messages") instanceof List ? ((List<?>) conv.get("messages")).size() : 0);
                    entity.setIsDeleted(0);
                    entity.setCreateTime(java.time.LocalDateTime.now());
                    entity.setLastMessageTime(java.time.LocalDateTime.now());
                    try {
                        conversationMapper.insert(entity);
                    } catch (Exception dupEx) {
                        // 主键冲突说明已存在，忽略
                    }
                } else if (existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
                    // 会话已被用户删除，拒绝重建（防止其他设备 syncConversations 把已删除会话推回来）
                    log.info("跳过同步已删除的会话: userId={}, convId={}", userId, convId);
                    continue;
                }
            } catch (Exception e) {
                log.debug("同步会话到DB失败: convId={}", conv.get("id"), e);
            }
        }
    }

    public List<Map<String, Object>> fetchConversations(Long userId) {
        String key = CONVERSATIONS_KEY + userId;
        String json = redisTemplate.opsForValue().get(key);

        // Redis中没有数据，从数据库加载并缓存（懒加载机制）
        if (json == null || json.isEmpty()) {
            log.info("Redis缓存未命中，从数据库加载对话列表: userId={}", userId);
            return loadConversationsFromDatabase(userId);
        }

        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("反序列化对话失败，从数据库重新加载: userId={}", userId, e);
            return loadConversationsFromDatabase(userId);
        }
    }

    /**
     * 从数据库加载对话列表并写入Redis缓存
     */
    private List<Map<String, Object>> loadConversationsFromDatabase(Long userId) {
        try {
            // 1. 查询数据库
            java.util.List<com.ai.gateway.entity.Conversation> conversations = conversationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ai.gateway.entity.Conversation>()
                            .eq(com.ai.gateway.entity.Conversation::getUserId, userId)
                            .eq(com.ai.gateway.entity.Conversation::getIsDeleted, 0)
                            .orderByDesc(com.ai.gateway.entity.Conversation::getLastMessageTime)
            );

            if (conversations.isEmpty()) {
                log.info("用户{}没有历史对话", userId);
                return new ArrayList<>();
            }

            // 2. 获取每个会话的最后一条消息作为预览
            Map<Long, String> previewMap = new HashMap<>();
            for (com.ai.gateway.entity.Conversation conv : conversations) {
                try {
                    // 查询最后一条消息
                    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ai.gateway.entity.ChatMessage> msgWrapper =
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                    msgWrapper.eq(com.ai.gateway.entity.ChatMessage::getConversationId, conv.getId())
                            .eq(com.ai.gateway.entity.ChatMessage::getUserId, userId)
                            .orderByDesc(com.ai.gateway.entity.ChatMessage::getId);

                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.ai.gateway.entity.ChatMessage> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1);
                    java.util.List<com.ai.gateway.entity.ChatMessage> lastMsgList = chatMessageMapper.selectList(page, msgWrapper);

                    if (!lastMsgList.isEmpty()) {
                        String content = lastMsgList.get(0).getContent();
                        // 截取前50个字符作为预览
                        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                        previewMap.put(conv.getId(), preview);
                    }
                } catch (Exception e) {
                    log.warn("获取会话预览失败: conversationId={}", conv.getId(), e);
                }
            }

            // 3. 转换为前端需要的格式
            List<Map<String, Object>> convList = conversations.stream()
                    .map(conv -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", String.valueOf(conv.getId()));
                        map.put("title", conv.getTitle());
                        map.put("model", conv.getModel());
                        map.put("messageCount", conv.getMessageCount());
                        map.put("lastMessageTime", conv.getLastMessageTime());
                        map.put("createTime", conv.getCreateTime());
                        map.put("previewContent", previewMap.getOrDefault(conv.getId(), "暂无消息"));
                        return map;
                    })
                    .collect(Collectors.toList());

            // 4. 写入Redis缓存（90天过期）
            String jsonData = objectMapper.writeValueAsString(convList);
            redisTemplate.opsForValue().set(CONVERSATIONS_KEY + userId, jsonData, 90, TimeUnit.DAYS);

            // 5. 更新版本号
            long version = System.currentTimeMillis();
            redisTemplate.opsForValue().set(CONVERSATIONS_VERSION_KEY + userId, String.valueOf(version), 90, TimeUnit.DAYS);

            log.info("✅ 从数据库加载对话列表成功: userId={}, count={}, version={}", userId, convList.size(), version);

            return convList;

        } catch (Exception e) {
            log.error("❌ 从数据库加载对话列表失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    public long getConversationVersion(Long userId) {
        String versionKey = CONVERSATIONS_VERSION_KEY + userId;
        String val = redisTemplate.opsForValue().get(versionKey);
        if (val == null) return 0;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 用户级每日用量统计（真实数据：token/调用次数来自 call_log，点数消耗来自 points_bill）
     *
     * @param days 统计天数（1-90）
     * @return 按日聚合的列表，缺失日期补 0
     */
    public Map<String, Object> getDailyUsage(Long userId, int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(safeDays - 1L);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        // 1. call_log 聚合：tokens / calls
        Map<LocalDate, long[]> usageByDay = new HashMap<>(); // [tokens, calls]
        LambdaQueryWrapper<com.ai.gateway.entity.CallLog> callWrapper = new LambdaQueryWrapper<>();
        callWrapper.eq(com.ai.gateway.entity.CallLog::getUserId, userId)
                .eq(com.ai.gateway.entity.CallLog::getStatus, 1)
                .ge(com.ai.gateway.entity.CallLog::getCreateTime, startDateTime);
        for (com.ai.gateway.entity.CallLog logEntry : callLogMapper.selectList(callWrapper)) {
            LocalDate day = logEntry.getCreateTime().toLocalDate();
            if (day.isBefore(startDate)) continue;
            long[] arr = usageByDay.computeIfAbsent(day, k -> new long[2]);
            int inTok = logEntry.getInputTokens() != null ? logEntry.getInputTokens() : 0;
            int outTok = logEntry.getOutputTokens() != null ? logEntry.getOutputTokens() : 0;
            arr[0] += inTok + outTok;
            arr[1] += 1;
        }

        // 2. points_bill 聚合：点数消耗（SETTLE 为实际消耗）
        Map<LocalDate, Double> pointsByDay = new HashMap<>();
        LambdaQueryWrapper<PointsBill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, Arrays.asList("SETTLE", "DEDUCT"))
                .lt(PointsBill::getPointsChange, BigDecimal.ZERO)
                .ge(PointsBill::getCreateTime, startDateTime);
        for (PointsBill bill : pointsBillMapper.selectList(billWrapper)) {
            LocalDate day = bill.getCreateTime().toLocalDate();
            if (day.isBefore(startDate)) continue;
            double change = bill.getPointsChange() != null ? Math.abs(bill.getPointsChange().doubleValue()) : 0;
            pointsByDay.merge(day, change, Double::sum);
        }

        // 3. 按日组装，缺失补 0
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("M/d");
        List<Map<String, Object>> daily = new ArrayList<>();
        long totalTokens = 0, totalCalls = 0;
        double totalPoints = 0;
        for (int i = 0; i < safeDays; i++) {
            LocalDate day = startDate.plusDays(i);
            long[] usage = usageByDay.getOrDefault(day, new long[2]);
            double points = pointsByDay.getOrDefault(day, 0D);
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.toString());
            item.put("label", day.format(labelFmt));
            item.put("tokens", usage[0]);
            item.put("calls", usage[1]);
            item.put("points", Math.round(points * 100) / 100.0);
            daily.add(item);
            totalTokens += usage[0];
            totalCalls += usage[1];
            totalPoints += points;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("days", safeDays);
        result.put("daily", daily);
        result.put("totalTokens", totalTokens);
        result.put("totalCalls", totalCalls);
        result.put("totalPoints", Math.round(totalPoints * 100) / 100.0);
        return result;
    }

    private UserInfoVO convertToVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(user, vo);
        UserContactVerification verifiedContact = contactVerificationMapper.selectById(user.getId());
        vo.setEmailVerified(verifiedContact != null
                && user.getEmail() != null
                && user.getEmail().equals(verifiedContact.getEmail()));
        vo.setPhoneVerified(verifiedContact != null
                && user.getPhone() != null
                && user.getPhone().equals(verifiedContact.getPhone()));
        return vo;
    }

    /**
     * 异步生成会话标题 (修复 P0-2)
     */
    @org.springframework.scheduling.annotation.Async("chatExecutor")
    public void generateTitleAsync(Long userId, Long conversationId) {
        try {
            // 获取该会话的第一条用户消息
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ai.gateway.entity.ChatMessage> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(com.ai.gateway.entity.ChatMessage::getConversationId, conversationId)
                   .eq(com.ai.gateway.entity.ChatMessage::getUserId, userId)
                   .eq(com.ai.gateway.entity.ChatMessage::getRole, "user")
                   .orderByAsc(com.ai.gateway.entity.ChatMessage::getId);
            
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.ai.gateway.entity.ChatMessage> page = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1);
            java.util.List<com.ai.gateway.entity.ChatMessage> firstMsgList = chatMessageMapper.selectList(page, wrapper);
            com.ai.gateway.entity.ChatMessage firstMsg = firstMsgList.isEmpty() ? null : firstMsgList.get(0);
            if (firstMsg != null && firstMsg.getContent() != null) {
                String prompt = "请用不超过10个字概括以下对话内容：" + firstMsg.getContent().substring(0, Math.min(50, firstMsg.getContent().length()));
                // 这里简化处理，实际应调用 AI 服务
                String generatedTitle = firstMsg.getContent().substring(0, Math.min(10, firstMsg.getContent().length()));
                
                com.ai.gateway.entity.Conversation conv = new com.ai.gateway.entity.Conversation();
                conv.setId(conversationId);
                conv.setTitle(generatedTitle);
                conversationMapper.updateById(conv);
                log.info("会话标题已自动生成: convId={}, title={}", conversationId, generatedTitle);
            }
        } catch (Exception e) {
            log.error("生成会话标题失败", e);
        }
    }

    /**
     * 记录充值营收到 platform_statistics 表
     * 如果今天已有记录则更新，否则插入新记录
     */
    private void recordRevenue(BigDecimal amount) {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();

            LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PlatformStatistics::getStatDate, today);
            PlatformStatistics todayStats = platformStatisticsMapper.selectOne(wrapper);

            if (todayStats != null) {
                BigDecimal currentRevenue = todayStats.getTotalRevenue() != null ? todayStats.getTotalRevenue() : BigDecimal.ZERO;
                BigDecimal currentRecharge = todayStats.getRechargeAmount() != null ? todayStats.getRechargeAmount() : BigDecimal.ZERO;
                todayStats.setTotalRevenue(currentRevenue.add(amount));
                todayStats.setRechargeAmount(currentRecharge.add(amount));
                platformStatisticsMapper.updateById(todayStats);
            } else {
                PlatformStatistics newStats = new PlatformStatistics();
                newStats.setStatDate(today);
                newStats.setTotalRevenue(amount);
                newStats.setRechargeAmount(amount);
                newStats.setTotalUsers(0);
                newStats.setNewUsers(0);
                newStats.setActiveUsers(0);
                newStats.setRetainedUsers(0);
                newStats.setTotalCalls(0L);
                newStats.setTotalTokens(0L);
                newStats.setTotalPointsConsumed(BigDecimal.ZERO);
                newStats.setPackageSales(0);
                newStats.setAvgOrderValue(amount);
                platformStatisticsMapper.insert(newStats);
            }

            log.info("营收记录已更新: date={}, amount={}", today, amount);
        } catch (Exception e) {
            log.error("记录营收数据失败: amount={}, error={}", amount, e.getMessage(), e);
        }
    }
}
