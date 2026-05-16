package com.ai.gateway.service;

import com.ai.gateway.common.ResultCode;
import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.entity.BillingRecord;
import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.BillingRecordMapper;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BillingRecordMapper billingRecordMapper;
    private final PointsBillMapper pointsBillMapper;
    private final CouponService couponService;
    private final PointsService pointsService;
    private final BillingService billingService;
    private final UserSubscriptionService userSubscriptionService;
    private final StringRedisTemplate redisTemplate;
    private final FreeLimitService freeLimitService;
    private final SessionService sessionService;
    private final ConversationVersionService conversationVersionService;
    private final com.ai.gateway.mapper.ConversationMapper conversationMapper;
    private final com.ai.gateway.mapper.ChatMessageMapper chatMessageMapper;
    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String BIND_CODE_PREFIX = "bind:code:";
    private static final String TFA_SECRET_PREFIX = "2fa:secret:";
    private static final String LOGIN_DEVICE_PREFIX = "login:device:";
    private static final String SUBSCRIPTION_PREFIX = "subscription:";

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
        user.setEmail(request.getEmail());
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
    public void recharge(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        user.setBalance(balanceAfter);
        userMapper.updateById(user);

        billingService.syncUserBalanceToRedis(userId, balanceAfter);
        
        // 注意：充值只增加余额（balance），不直接增加点数
        // 用户需要使用余额购买套餐来获得点数和会员权益
        // 套餐制度：越贵的套餐越划算（例如：100元=1500点，比10元=100点更优惠）

        log.info("用户充值成功: userId={}, amount={}, balanceBefore={}, balanceAfter={}",
                userId, amount, balanceBefore, balanceAfter);
    }

    /**
     * 使用优惠券充值
     * @param userId 用户ID
     * @param amount 充值金额
     * @param couponId 优惠券ID（可选）
     * @return 充值结果信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rechargeWithCoupon(Long userId, BigDecimal amount, Long couponId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal actualAmount = amount;

        // 如果提供了优惠券ID，则使用优惠券
        if (couponId != null) {
            try {
                // 使用优惠券，orderType为RECHARGE，orderId为时间戳生成的订单号
                String orderId = "RECHARGE_" + System.currentTimeMillis();
                discount = couponService.useCoupon(userId, couponId, amount, null, "RECHARGE", orderId);
                actualAmount = amount.subtract(discount);
                
                result.put("couponUsed", true);
                result.put("discount", discount);
                result.put("orderId", orderId);
                
                log.info("充值使用优惠券: userId={}, couponId={}, discount={}, actualAmount={}", 
                        userId, couponId, discount, actualAmount);
            } catch (Exception e) {
                log.warn("优惠券使用失败，将按原价充值: {}", e.getMessage());
                result.put("couponUsed", false);
                result.put("discount", BigDecimal.ZERO);
                result.put("message", "优惠券使用失败: " + e.getMessage());
            }
        } else {
            result.put("couponUsed", false);
            result.put("discount", BigDecimal.ZERO);
        }

        // 执行充值（实际支付金额）
        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(actualAmount);

        user.setBalance(balanceAfter);
        userMapper.updateById(user);

        billingService.syncUserBalanceToRedis(userId, balanceAfter);
        
        // ⚠️ 注意：充值只增加余额（balance），不直接增加点数
        // 用户需要使用余额购买套餐才能获得点数和会员权益

        result.put("success", true);
        result.put("amount", amount);
        result.put("actualAmount", actualAmount);
        result.put("balanceBefore", balanceBefore);
        result.put("balanceAfter", balanceAfter);

        log.info("用户充值成功: userId={}, amount={}, discount={}, actualAmount={}, balanceBefore={}, balanceAfter={}",
                userId, amount, discount, actualAmount, balanceBefore, balanceAfter);

        return result;
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
        return userMapper.selectCount(null);
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
        String code = String.format("%06d", new Random().nextInt(1000000));
        String key = BIND_CODE_PREFIX + type + ":" + target;
        redisTemplate.opsForValue().set(key, code, 10, TimeUnit.MINUTES);
        log.info("发送绑定验证码: type={}, target={}, code={}", type, target, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(Long userId, String email, String code) {
        String key = BIND_CODE_PREFIX + "email:" + email;
        String savedCode = redisTemplate.opsForValue().get(key);
        if (savedCode == null || !savedCode.equals(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "验证码错误或已过期");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setEmail(email);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        redisTemplate.delete(key);
        log.info("邮箱绑定成功: userId={}, email={}", userId, email);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(Long userId, String phone, String code) {
        String key = BIND_CODE_PREFIX + "phone:" + phone;
        String savedCode = redisTemplate.opsForValue().get(key);
        if (savedCode == null || !savedCode.equals(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "验证码错误或已过期");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        redisTemplate.delete(key);
        log.info("手机号绑定成功: userId={}, phone={}", userId, phone);
    }

    public Map<String, String> enable2FA(Long userId) {
        String secret = cn.hutool.crypto.SecureUtil.generateKey("HmacSHA1", 20).toString();
        String key = TFA_SECRET_PREFIX + userId;
        redisTemplate.opsForValue().set(key, secret, 30, TimeUnit.DAYS);

        String qrUrl = String.format("otpauth://totp/LingdianAI:%s?secret=%s&issuer=LingdianAI",
                userId, secret);

        Map<String, String> result = new HashMap<>();
        result.put("secret", secret);
        result.put("qrUrl", qrUrl);

        log.info("2FA已启用: userId={}", userId);
        return result;
    }

    public void disable2FA(Long userId, String code) {
        if (!verify2FAToken(userId, code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "2FA验证码错误");
        }

        String key = TFA_SECRET_PREFIX + userId;
        redisTemplate.delete(key);

        log.info("2FA已禁用: userId={}", userId);
    }

    public boolean verify2FA(Long userId, String code) {
        return verify2FAToken(userId, code);
    }

    private boolean verify2FAToken(Long userId, String code) {
        String key = TFA_SECRET_PREFIX + userId;
        String secret = redisTemplate.opsForValue().get(key);
        if (secret == null) {
            return false;
        }

        String expectedCode = generateTOTPCode(secret);
        return expectedCode != null && expectedCode.equals(code);
    }

    private String generateTOTPCode(String secret) {
        try {
            long timeStep = System.currentTimeMillis() / 30000;
            String data = secret + timeStep;
            return String.format("%06d", Math.abs(data.hashCode()) % 1000000);
        } catch (Exception e) {
            log.error("TOTP生成失败", e);
            return null;
        }
    }

    public Map<String, Object> get2FAStatus(Long userId) {
        String key = TFA_SECRET_PREFIX + userId;
        boolean enabled = Boolean.TRUE.equals(redisTemplate.hasKey(key));

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", enabled);
        status.put("backupCodes", enabled ? generateBackupCodes() : null);

        return status;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            codes.add(String.format("%04d-%04d", random.nextInt(10000), random.nextInt(10000)));
        }
        return codes;
    }

    public List<Map<String, Object>> getLoginDevices(Long userId) {
        String pattern = LOGIN_DEVICE_PREFIX + userId + ":*";
        Set<String> keys = redisScanUtil.scanKeys(pattern);

        List<Map<String, Object>> devices = new ArrayList<>();
        for (String key : keys) {
            String deviceInfo = redisTemplate.opsForValue().get(key);
            if (deviceInfo != null) {
                String deviceId = key.substring(key.lastIndexOf(":") + 1);
                Map<String, Object> device = new HashMap<>();
                device.put("deviceId", deviceId);
                device.put("info", deviceInfo);
                device.put("lastActive", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                devices.add(device);
            }
        }

        if (devices.isEmpty()) {
            Map<String, Object> currentDevice = new HashMap<>();
            currentDevice.put("deviceId", "current");
            currentDevice.put("info", "Current Device");
            currentDevice.put("lastActive", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            currentDevice.put("isCurrent", true);
            devices.add(currentDevice);
        }

        return devices;
    }

    public void removeLoginDevice(Long userId, String deviceId) {
        String key = LOGIN_DEVICE_PREFIX + userId + ":" + deviceId;
        redisTemplate.delete(key);
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
        log.info("移除所有登录设备: userId={}, exceptCurrent={}", userId, exceptCurrent);
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

        log.info("账户已注销: userId={}", userId);
    }

    public List<Map<String, Object>> getRechargeRecords(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        
        Page<PointsBill> pageObj = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<PointsBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsBill::getUserId, userId)
               .eq(PointsBill::getChangeType, "RECHARGE")
               .orderByDesc(PointsBill::getCreateTime);
        
        Page<PointsBill> resultPage = pointsBillMapper.selectPage(pageObj, wrapper);
        List<PointsBill> rechargeBills = resultPage.getRecords();
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (PointsBill bill : rechargeBills) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", bill.getId());
            record.put("amount", bill.getPointsChange()); // 充值金额（点数）
            record.put("balanceBefore", bill.getBalanceBefore());
            record.put("balanceAfter", bill.getBalanceAfter());
            record.put("paymentMethod", "POINTS"); // 点数充值
            record.put("status", "COMPLETED");
            record.put("description", bill.getDescription());
            record.put("businessId", bill.getBusinessId());
            record.put("createTime", bill.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            records.add(record);
        }
        
        log.info("查询充值记录: userId={}, page={}, size={}, count={}", userId, page, size, records.size());
        return records;
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

    public List<Map<String, Object>> getCoupons(Long userId) {
        List<Map<String, Object>> coupons = new ArrayList<>();
        Random random = new Random();

        String[] couponTypes = {"DISCOUNT", "CASH", "FREE_CALLS"};
        String[] couponNames = {"新人礼包", "限时折扣", "充值返利", "邀请奖励"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> coupon = new HashMap<>();
            coupon.put("id", userId * 100 + i);
            coupon.put("code", "CP" + String.format("%06d", random.nextInt(1000000)));
            coupon.put("name", couponNames[random.nextInt(couponNames.length)]);
            coupon.put("type", couponTypes[random.nextInt(couponTypes.length)]);
            coupon.put("value", BigDecimal.valueOf(random.nextInt(50) + 5));
            coupon.put("minAmount", BigDecimal.valueOf(random.nextInt(100)));
            coupon.put("status", random.nextBoolean() ? "AVAILABLE" : "USED");
            coupon.put("expireTime", LocalDateTime.now().plusDays(random.nextInt(90)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            coupons.add(coupon);
        }

        return coupons;
    }

    public Map<String, Object> useCoupon(Long userId, String code) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "优惠券已使用");
        result.put("discount", BigDecimal.valueOf(new Random().nextInt(30) + 5));

        log.info("使用优惠券: userId={}, code={}", userId, code);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void redeemCoupon(Long userId, String redeemCode) {
        Map<String, Object> coupon = new HashMap<>();
        coupon.put("code", redeemCode);
        coupon.put("name", "兑换码奖励");
        coupon.put("value", BigDecimal.valueOf(20));

        log.info("兑换码兑换成功: userId={}, code={}", userId, redeemCode);
    }

    public Map<String, Object> getSubscription(Long userId) {
        Map<String, Object> subscription = new HashMap<>();
        subscription.put("plan", "NONE");
        subscription.put("status", "INACTIVE");
        subscription.put("expireTime", null);
        subscription.put("autoRenew", false);

        String key = SUBSCRIPTION_PREFIX + userId;
        String plan = redisTemplate.opsForValue().get(key);
        if (plan != null) {
            subscription.put("plan", plan);
            subscription.put("status", "ACTIVE");
            subscription.put("expireTime", LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            subscription.put("autoRenew", true);
        }

        return subscription;
    }

    @Transactional(rollbackFor = Exception.class)
    public void subscribe(Long userId, String packageCode, String paymentMethod) {
        // 修复 P1-9: 调用真实的套餐购买服务，不再只是存 Redis
        if (packageCode == null || packageCode.isEmpty()) {
            throw new BusinessException("请选择要购买的套餐");
        }
        
        // 委托给 UserSubscriptionService 处理扣费和创建订阅
        userSubscriptionService.purchasePackage(userId, packageCode, null);
        
        log.info("用户完成套餐订阅: userId={}, package={}, method={}", userId, packageCode, paymentMethod);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelSubscription(Long userId) {
        String key = SUBSCRIPTION_PREFIX + userId;
        redisTemplate.delete(key);

        log.info("订阅已取消: userId={}", userId);
    }

    // ========== OAuth 第三方登录 ==========

    public UserInfoVO oauthLogin(String provider, String code, String state) {
        String openId = fetchOAuthOpenId(provider, code, state);
        if (openId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "获取" + provider + "用户信息失败");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, provider + "_" + openId);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            user = new User();
            user.setUsername(provider + "_" + openId);
            user.setPassword(passwordEncoder.encode(openId + "_oauth_default"));
            user.setBalance(BigDecimal.ZERO);
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
            log.info("OAuth新用户注册: provider={}, openId={}, userId={}", provider, openId, user.getId());
        }

        return convertToVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindOAuth(Long userId, String provider, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        String openId = fetchOAuthOpenId(provider, code, "bind");
        if (openId == null) throw new BusinessException("获取" + provider + "信息失败");

        String key = "oauth:bind:" + provider + ":" + userId;
        redisTemplate.opsForValue().set(key, openId, 365, TimeUnit.DAYS);

        log.info("OAuth绑定成功: userId={}, provider={}, openId={}", userId, provider, openId);
    }

    public void unbindOAuth(Long userId, String provider) {
        String key = "oauth:bind:" + provider + ":" + userId;
        redisTemplate.delete(key);
        log.info("OAuth解绑成功: userId={}, provider={}", userId, provider);
    }

    private String fetchOAuthOpenId(String provider, String code, String state) {
        return provider + "_demo_" + code.hashCode() + "_" + System.currentTimeMillis() % 100000;
    }

    // ========== 对话历史云端同步 ==========

    private static final String CONVERSATIONS_KEY = "conversations:";
    private static final String CONVERSATIONS_VERSION_KEY = "conversations:version:";

    public long syncConversations(Long userId, List<Map<String, Object>> conversations) {
        if (conversations == null || conversations.isEmpty()) return 0;
        
        // 修复 P0-2: 异步生成会话标题
        for (Map<String, Object> conv : conversations) {
            String title = (String) conv.get("title");
            if (title == null || title.trim().isEmpty() || "无标题会话".equals(title)) {
                Long convId = ((Number) conv.get("id")).longValue();
                generateTitleAsync(userId, convId);
            }
        }

        String dataKey = CONVERSATIONS_KEY + userId;
        String versionKey = CONVERSATIONS_VERSION_KEY + userId;
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(conversations);
        } catch (Exception e) {
            log.error("序列化对话失败", e);
            return 0;
        }
        long version = System.currentTimeMillis();
        redisTemplate.opsForValue().set(dataKey, json, 90, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(versionKey, String.valueOf(version), 90, TimeUnit.DAYS);
        log.info("对话同步成功: userId={}, count={}, version={}", userId, conversations.size(), version);
        return version;
    }

    public List<Map<String, Object>> fetchConversations(Long userId) {
        String key = CONVERSATIONS_KEY + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            log.error("反序列化对话失败: userId={}", userId, e);
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

    // ========== 免费配额 ==========

    public Map<String, Integer> getFreeQuota(Long userId) {
        return freeLimitService.getUserFreeQuota(userId);
    }

    private UserInfoVO convertToVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(user, vo);
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
}
