package com.ai.gateway.service;

import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.PointsTemporaryLot;
import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.User;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.PointsTemporaryLotMapper;
import com.ai.gateway.mapper.AiChargeIntentMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 点数管理服务 - 统一使用点数作为AI对话消耗单位
 */
@Slf4j
@Service
public class PointsService {

    private final PointsBillMapper pointsBillMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final PlatformModelService platformModelService;
    private final com.ai.gateway.mapper.BillingRecordMapper billingRecordMapper;
    private final PointsTemporaryLotMapper temporaryLotMapper;
    private final AiChargeIntentMapper aiChargeIntentMapper;

    public PointsService(PointsBillMapper pointsBillMapper,
                         StringRedisTemplate redisTemplate,
                         UserMapper userMapper,
                         PlatformModelService platformModelService,
                         com.ai.gateway.mapper.BillingRecordMapper billingRecordMapper,
                         PointsTemporaryLotMapper temporaryLotMapper,
                         AiChargeIntentMapper aiChargeIntentMapper) {
        this.pointsBillMapper = pointsBillMapper;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.platformModelService = platformModelService;
        this.billingRecordMapper = billingRecordMapper;
        this.temporaryLotMapper = temporaryLotMapper;
        this.aiChargeIntentMapper = aiChargeIntentMapper;
    }
    
    private static final String USER_POINTS_PREFIX = "points:user:";
    private static final String POINTS_PRECISION_KEY = "points:precision";
    private static final String POINTS_PRE_DEDUCT_PREFIX = "points:pre_deduct:";
    private static final String POINTS_REFUND_PREFIX = "points:refund:";
    private static final String POINTS_SETTLEMENT_PREFIX = "points:settlement:";

    private DefaultRedisScript<List> pointsBillingScript;
    private DefaultRedisScript<List> settlementScript;
    private DefaultRedisScript<List> temporaryExpiryScript;

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        pointsBillingScript = new DefaultRedisScript<>();
        pointsBillingScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/points_billing.lua")));
        pointsBillingScript.setResultType((Class<List>) (Class<?>) List.class);

        settlementScript = new DefaultRedisScript<>();
        settlementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/settlement.lua")));
        settlementScript.setResultType((Class<List>) (Class<?>) List.class);

        temporaryExpiryScript = new DefaultRedisScript<>();
        temporaryExpiryScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/expire_temporary_points.lua")));
        temporaryExpiryScript.setResultType((Class<List>) (Class<?>) List.class);

        log.info("点数计费Lua脚本加载成功");
        log.info("点数结算Lua脚本加载成功");
        log.info("限时点数过期Lua脚本加载成功");
    }

    /**
     * 获取用户当前点数（从Redis缓存，带防击穿机制）
     * @param userId 用户ID
     * @return 点数，保留两位小数
     */
    public BigDecimal getUserPoints(Long userId) {
        String key = USER_POINTS_PREFIX + userId;
        String pointsStr = redisTemplate.opsForValue().get(key);
        
        if (pointsStr != null) {
            try {
                return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.error("解析点数失败: userId={}, pointsStr={}", userId, pointsStr);
            }
        }
        
        // 缓存未命中，使用分布式锁防止击穿
        String lockKey = "lock:points:" + userId;
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, java.util.concurrent.TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(isLocked)) {
            try {
                // 双重检查
                pointsStr = redisTemplate.opsForValue().get(key);
                if (pointsStr != null) {
                    return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
                }
                return syncUserPointsFromDB(userId);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 等待一小段时间后重试获取缓存
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            pointsStr = redisTemplate.opsForValue().get(key);
            if (pointsStr != null) {
                return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO; // 极端情况下返回0，避免阻塞
        }
    }

    /**
     * 预扣点数（对话前检查 + 原子性预扣）
     * 使用 Lua 脚本保证原子性：检查余额 -> 预扣 -> 记录预扣标记
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param modelName 模型名称
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean preDeductPoints(Long userId, String requestId, String modelName) {
        BigDecimal pointsCost = calculatePointsCost(modelName);
        if (pointsCost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        User lockedUser = lockUserPointsRow(userId);
        ensurePointsCache(lockedUser);
        return doPreDeduct(userId, requestId, pointsCost);
    }

    /**
     * 执行预扣（Lua 原子操作）
     */
    private boolean doPreDeduct(Long userId, String requestId, BigDecimal points) {
        BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;

        try {
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey),
                // The durable ai_charge_intent row, rather than Redis TTL, controls recovery.
                userId.toString(), deductPoints.toPlainString(), requestId, "0"
            );

            if (result != null && !result.isEmpty()) {
                long success = ((Number) result.get(0)).longValue();
                if (success == 1) {
                    String currentPointsStr = result.get(1).toString();
                    BigDecimal balanceAfter = new BigDecimal(currentPointsStr);
                    log.info("点数预扣成功(Lua): userId={}, deduct={}, requestId={}, balance={}",
                            userId, deductPoints, requestId, balanceAfter);
                    return true;
                } else {
                    log.warn("点数预扣失败(余额不足): userId={}, need={}, requestId={}",
                            userId, deductPoints, requestId);
                    return false;
                }
            }
            log.warn("点数预扣返回空: userId={}, requestId={}", userId, requestId);
            return false;
        } catch (Exception e) {
            log.error("点数预扣异常: userId={}, requestId={}", userId, requestId, e);
            return false;
        }
    }

    /**
     * 结算点数（对话后根据实际消耗结算）
     * 如果预扣金额 > 实际消耗，退还差额
     * 如果预扣金额 < 实际消耗，补扣差额
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param actualPoints 实际消耗点数
     * @param modelName 模型名称
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean settlePoints(Long userId, String requestId, BigDecimal actualPoints, String modelName) {
        return settlePoints(userId, requestId, actualPoints, modelName, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean settlePoints(Long userId, String requestId, BigDecimal actualPoints, String modelName,
                                BigDecimal fallbackPreDeduct, boolean reservationConfirmed) {
        BigDecimal actual = actualPoints.setScale(3, RoundingMode.HALF_DOWN);
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;
        String settledKey = "points:settlement:" + requestId;

        try {
            User lockedUser = lockUserPointsRow(userId);
            long currentPointsVersion = lockedUser.getPointsVersion() == null ? 0L : lockedUser.getPointsVersion();
            ensurePointsCache(lockedUser);
            // 数据库唯一键是最终账务去重屏障；重放必须同时匹配用户和实际扣费金额。
            com.ai.gateway.entity.BillingRecord existing = billingRecordMapper.selectOne(
                    new LambdaQueryWrapper<com.ai.gateway.entity.BillingRecord>()
                            .eq(com.ai.gateway.entity.BillingRecord::getRequestId, requestId));
            if (existing != null) return isSameSettlement(existing, userId, actual);

            String settlementAttemptTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            List<Object> result = redisTemplate.execute(
                settlementScript,
                List.of(pointsKey, preDeductKey, settledKey, POINTS_REFUND_PREFIX + requestId),
                actual.toPlainString(), requestId, Long.toString(currentPointsVersion), settlementAttemptTime,
                reservationConfirmed && fallbackPreDeduct != null
                        ? fallbackPreDeduct.setScale(3, RoundingMode.HALF_DOWN).toPlainString() : "0"
            );

            if (result == null || result.size() < 5) {
                log.error("结算Lua脚本返回无效: userId={}, requestId={}", userId, requestId);
                return false;
            }
            long success = ((Number) result.get(0)).longValue();
            if (success == -2) {
                log.error("结算重放缺少余额版本元数据，拒绝应用旧快照: userId={}, requestId={}", userId, requestId);
                return false;
            }
            if (success != 1 && success != 2) {
                log.warn("结算失败: userId={}, requestId={}, reason={}",
                        userId, requestId, result.size() > 1 ? result.get(1) : "unknown");
                return false;
            }

            // 状态 2 表示 Redis 已对该 requestId 结算过；保存的版本用于识别期间是否已有更新余额。
            String balanceAfterStr = result.get(1).toString();
            BigDecimal preDeductAmount = new BigDecimal(result.get(2).toString());
            long settlementPointsVersion = Long.parseLong(result.get(3).toString());
            LocalDateTime settlementTime = LocalDateTime.parse(result.get(4).toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            BigDecimal balanceAfter = new BigDecimal(balanceAfterStr).setScale(3, RoundingMode.HALF_DOWN);
            BigDecimal difference = preDeductAmount.subtract(actual);

            if (currentPointsVersion < settlementPointsVersion) {
                log.error("结算版本高于当前数据库版本，拒绝回滚余额: userId={}, requestId={}, currentVersion={}, settlementVersion={}",
                        userId, requestId, currentPointsVersion, settlementPointsVersion);
                return false;
            }

            // 先插入带唯一 request_id 的流水作为 DB 幂等锚点，再写点数明细和余额镜像。
            // 若进程在 Redis 成功后、数据库提交前退出，重试会命中 Lua 的状态 2 并补齐这些行。
            com.ai.gateway.entity.BillingRecord consumption = new com.ai.gateway.entity.BillingRecord();
            consumption.setUserId(userId);
            consumption.setRequestId(requestId);
            consumption.setAmount(actual);
            consumption.setType(1);
            consumption.setBalanceBefore(balanceAfter.add(actual));
            consumption.setBalanceAfter(balanceAfter);
            consumption.setCreateTime(settlementTime);
            try {
                billingRecordMapper.insert(consumption);
            } catch (DuplicateKeyException duplicate) {
                com.ai.gateway.entity.BillingRecord duplicateRecord = billingRecordMapper.selectOne(
                        new LambdaQueryWrapper<com.ai.gateway.entity.BillingRecord>()
                                .eq(com.ai.gateway.entity.BillingRecord::getRequestId, requestId));
                if (duplicateRecord != null && isSameSettlement(duplicateRecord, userId, actual)) {
                    log.info("请求扣费流水已由并发请求写入，按幂等成功处理: requestId={}", requestId);
                    return true;
                }
                throw duplicate;
            }

            consumeTemporaryLots(userId, actual);
            recordBill(userId, "SETTLE", actual.negate(), balanceAfter.add(actual), balanceAfter,
                    requestId, "对话结算: " + modelName + " (预扣:" + preDeductAmount + ", 实际:" + actual + ")",
                    modelName, null, null, settlementTime);

            // 重放快照只能补 DB 尚未提交的这一代余额；版本更高说明后续点数操作已提交。
            if (currentPointsVersion == settlementPointsVersion) {
                updateUserPointsInDB(userId, balanceAfter);
            }

            log.info("点数结算成功(Lua): userId={}, preDeduct={}, actual={}, difference={}, balance={}",
                    userId, preDeductAmount, actual, difference, balanceAfter);
            return true;
        } catch (Exception e) {
            log.error("点数结算异常: userId={}, requestId={}", userId, requestId, e);
            // Redis 已可能完成原子结算；DB 事务失败时回滚本地账单，outbox 重投会按 Lua 快照补写。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    private boolean isSameSettlement(com.ai.gateway.entity.BillingRecord existing, Long userId, BigDecimal actual) {
        return existing.getUserId() != null && existing.getUserId().equals(userId)
                && existing.getAmount() != null && existing.getAmount().compareTo(actual) == 0
                && Integer.valueOf(1).equals(existing.getType());
    }

    /**
     * 回滚预扣（对话失败时调用 - 修复 P1-3: 使用 Lua 脚本保证原子性）
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackPreDeduct(Long userId, String requestId) {
        rollbackPreDeduct(userId, requestId, null, true);
    }

    /**
     * Idempotently refund a durable reservation. The fallback amount recovers pre-deducts
     * whose old Redis reservation key expired before this version removed that TTL.
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackPreDeduct(Long userId, String requestId, BigDecimal fallbackPoints,
                                     boolean reservationConfirmed) {
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;
        String refundKey = POINTS_REFUND_PREFIX + requestId;
        String settledKey = POINTS_SETTLEMENT_PREFIX + requestId;

        try {
            User lockedUser = lockUserPointsRow(userId);
            Object settledMarker = redisTemplate.opsForHash().get(settledKey, "requestId");
            if (settledMarker != null) {
                log.error("拒绝回滚已有结算快照的请求: userId={}, requestId={}", userId, requestId);
                return false;
            }
            BigDecimal fallback = !reservationConfirmed || fallbackPoints == null ? BigDecimal.ZERO : fallbackPoints
                    .setScale(3, RoundingMode.HALF_DOWN);
            long databasePointsVersion = lockedUser.getPointsVersion() == null ? 0L : lockedUser.getPointsVersion();
            BigDecimal databaseBalance = lockedUser.getPoints() == null ? BigDecimal.ZERO : lockedUser.getPoints();
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey, refundKey, settledKey),
                userId.toString(), "0", requestId, "0", fallback.toPlainString(),
                Long.toString(databasePointsVersion),
                databaseBalance.setScale(3, RoundingMode.HALF_DOWN).toPlainString()
            );
            if (result == null || result.isEmpty()) return false;
            long status = ((Number) result.get(0)).longValue();
            if (status == -1) {
                log.error("拒绝回滚已结算请求: userId={}, requestId={}", userId, requestId);
                return false;
            }
            if (status == 0 || status == 3) return true;
            if (status != 1 && status != 2) return false;
            if (result.size() < 4) return false;

            BigDecimal balanceAfter = new BigDecimal(result.get(1).toString())
                    .setScale(3, RoundingMode.HALF_DOWN);
            long refundPointsVersion = Long.parseLong(result.get(2).toString());
            BigDecimal refundedPoints = result.size() >= 4
                    ? new BigDecimal(result.get(3).toString()).setScale(3, RoundingMode.HALF_DOWN)
                    : BigDecimal.ZERO;
            if (databasePointsVersion < refundPointsVersion) {
                log.error("退款标记版本高于数据库版本，拒绝覆盖余额: userId={}, requestId={}, currentVersion={}, refundVersion={}",
                        userId, requestId, databasePointsVersion, refundPointsVersion);
                return false;
            }
            if (databasePointsVersion == refundPointsVersion) {
                if (refundedPoints.signum() > 0) {
                    recordBill(userId, "ROLLBACK", refundedPoints, balanceAfter.subtract(refundedPoints),
                            balanceAfter, requestId, "AI 请求失败返还预扣点数", null, null, 0);
                }
                updateUserPointsInDB(userId, balanceAfter);
            }
            log.info("点数预扣回滚成功: userId={}, requestId={}, balance={}", userId, requestId, balanceAfter);
            return true;
        } catch (Exception e) {
            log.error("点数预扣回滚异常: userId={}, requestId={}", userId, requestId, e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    /** Remove the Redis settlement replay marker only after the outbox is durably settled. */
    public void cleanupSettlementSnapshot(String requestId) {
        redisTemplate.delete(POINTS_SETTLEMENT_PREFIX + requestId);
    }

    /**
     * 计算模型点数消耗
     */
    private BigDecimal calculatePointsCost(String modelName) {
        PlatformModelConfig config = getModelConfigByName(modelName);
        if (config == null) {
            return BigDecimal.ONE;
        }
        if (config.getFixedPoints() != null) {
            return config.getFixedPoints();
        }
        return BigDecimal.ONE;
    }

    private PlatformModelConfig getModelConfigByName(String modelName) {
        try {
            return platformModelService.getByDisplayName(modelName);
        } catch (Exception e) {
            log.warn("获取模型配置失败: modelName={}", modelName, e);
            return null;
        }
    }

    /**
     * 扣除点数（对话消耗 - 原子性操作）
     * @deprecated 使用 preDeductPoints + settlePoints 替代
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean deductPoints(Long userId, BigDecimal points, String businessId, String modelName) {
        // 保留三位小数精度进行计算
        BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + businessId;

        try {
            User lockedUser = lockUserPointsRow(userId);
            ensurePointsCache(lockedUser);
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey),
                userId.toString(), deductPoints.toPlainString(), businessId, "60"
            );

            if (result != null && !result.isEmpty()) {
                long success = ((Number) result.get(0)).longValue();
                if (success == 1) {
                    // 记录账单
                    String currentPointsStr = result.get(1).toString();
                    BigDecimal balanceAfter = new BigDecimal(currentPointsStr);
                    BigDecimal balanceBefore = balanceAfter.add(deductPoints);
                    recordBill(userId, "DEDUCT", deductPoints.negate(), balanceBefore, balanceAfter,
                            businessId, "对话消耗: " + modelName, modelName, null, null);

                    consumeTemporaryLots(userId, deductPoints);
                    
                    // 同步数据库
                    updateUserPointsInDB(userId, balanceAfter);
                    
                    log.info("点数扣除成功(Lua): userId={}, deduct={}, balance={}", userId, deductPoints, balanceAfter);
                    return true;
                }
            }
            log.warn("点数扣除失败(Lua): userId={}, deduct={}", userId, deductPoints);
            return false;
        } catch (Exception e) {
            log.error("点数扣除异常: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 增加点数（充值或发放）
     * @param userId 用户ID
     * @param points 增加点数（保留三位小数精度）
     * @param changeType 变动类型：RECHARGE-充值, GRANT-发放
     * @param businessId 业务ID
     * @param description 描述
     * @param expireTime 有效期（可选）
     * @return 新余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal addPoints(Long userId, BigDecimal points, String changeType,
                                String businessId, String description, LocalDateTime expireTime,
                                Integer isTemporary) {
        BigDecimal addPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        String key = USER_POINTS_PREFIX + userId;

        // All balance writers lock the same MySQL user row before touching Redis. This keeps
        // pre-deduct, settlement, rollback and grants in one order across backend instances.
        User lockedUser = lockUserPointsRow(userId);
        BigDecimal current = ensurePointsCache(lockedUser);

        boolean temporaryGrant = Integer.valueOf(1).equals(isTemporary);
        boolean dailyPermanentGrant = "DAILY_PERMANENT_GRANT".equals(changeType);
        if ((temporaryGrant || dailyPermanentGrant) && businessId != null && !businessId.isBlank()) {
            PointsBill previousGrant = pointsBillMapper.selectOne(new LambdaQueryWrapper<PointsBill>()
                    .eq(PointsBill::getUserId, userId)
                    .eq(PointsBill::getBusinessId, businessId)
                    .eq(PointsBill::getChangeType, changeType)
                    .last("LIMIT 1"));
            if (previousGrant != null) {
                log.info("每日点数发放幂等命中: userId={}, businessId={}", userId, businessId);
                return current;
            }
        }

        if (temporaryGrant && (expireTime == null || addPoints.signum() <= 0)) {
            throw new IllegalArgumentException("限时点数必须为正数且需要设置过期时间");
        }
        BigDecimal newBalance = current.add(addPoints).setScale(3, RoundingMode.HALF_DOWN);

        if (temporaryGrant) {
            PointsTemporaryLot lot = new PointsTemporaryLot();
            lot.setUserId(userId);
            lot.setBusinessId(businessId);
            lot.setGrantedPoints(addPoints);
            lot.setRemainingPoints(addPoints);
            lot.setExpireTime(expireTime);
            lot.setStatus("ACTIVE");
            lot.setCreateTime(LocalDateTime.now());
            temporaryLotMapper.insert(lot);
        } else if (addPoints.signum() < 0) {
            consumeTemporaryLots(userId, addPoints.abs());
        }

        recordBill(userId, changeType, addPoints, current, newBalance,
                businessId, description, null, expireTime, isTemporary);

        // Do not repopulate this key from the database: it can already contain an unsettled
        // reservation that is intentionally not reflected in the database yet.
        redisTemplate.opsForValue().set(key, newBalance.toPlainString());
        updateUserPointsInDB(userId, newBalance);
        
        log.info("点数增加成功: userId={}, add={}, type={}, balance={}", 
                userId, addPoints, changeType, newBalance);
        
        return newBalance;
    }

    /**
     * 同步用户点数从数据库到Redis
     * @param userId 用户ID
     * @return 点数
     */
    private BigDecimal syncUserPointsFromDB(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            return BigDecimal.ZERO;
        }
        
        BigDecimal points = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
        String key = USER_POINTS_PREFIX + userId;
        redisTemplate.opsForValue().setIfAbsent(key, points.setScale(3, RoundingMode.HALF_DOWN).toPlainString());
        String currentPoints = redisTemplate.opsForValue().get(key);
        BigDecimal effectivePoints = currentPoints != null ? new BigDecimal(currentPoints) : points;

        log.info("确保用户点数缓存存在: userId={}, points={}", userId, effectivePoints);
        return effectivePoints;
    }

    private User lockUserPointsRow(Long userId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) throw new com.ai.gateway.exception.BusinessException(ResultCode.USER_NOT_FOUND);
        return user;
    }

    private BigDecimal ensurePointsCache(User user) {
        String key = USER_POINTS_PREFIX + user.getId();
        String currentPoints = redisTemplate.opsForValue().get(key);
        if (currentPoints == null) {
            BigDecimal databasePoints = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
            redisTemplate.opsForValue().setIfAbsent(key,
                    databasePoints.setScale(3, RoundingMode.HALF_DOWN).toPlainString());
            currentPoints = redisTemplate.opsForValue().get(key);
        }
        if (currentPoints == null) {
            throw new IllegalStateException("用户点数缓存初始化失败: userId=" + user.getId());
        }
        return new BigDecimal(currentPoints).setScale(3, RoundingMode.HALF_DOWN);
    }

    /**
     * 更新用户点数到数据库
     * @param userId 用户ID
     * @param newPoints 新点数
     */
    private void updateUserPointsInDB(Long userId, BigDecimal newPoints) {
        int updated = userMapper.updatePointsAndIncrementVersion(userId, newPoints);
        if (updated != 1) {
            throw new IllegalStateException("更新用户点数或余额版本失败: userId=" + userId);
        }
        
        log.debug("更新用户点数到数据库: userId={}, points={}", userId, newPoints);
    }

    /**
     * 记录点数账单
     * @param userId 用户ID
     * @param changeType 变动类型
     * @param pointsChange 变动点数
     * @param balanceBefore 变动前余额
     * @param balanceAfter 变动后余额
     * @param businessId 业务ID
     * @param description 描述
     * @param modelName 模型名称
     * @param expireTime 有效期
     */
    private void recordBill(Long userId, String changeType, BigDecimal pointsChange,
                           BigDecimal balanceBefore, BigDecimal balanceAfter,
                           String businessId, String description, String modelName,
                            LocalDateTime expireTime, Integer isTemporary) {
        recordBill(userId, changeType, pointsChange, balanceBefore, balanceAfter,
                businessId, description, modelName, expireTime, isTemporary, LocalDateTime.now());
    }

    private void recordBill(Long userId, String changeType, BigDecimal pointsChange,
                           BigDecimal balanceBefore, BigDecimal balanceAfter,
                           String businessId, String description, String modelName,
                           LocalDateTime expireTime, Integer isTemporary, LocalDateTime createTime) {
        PointsBill bill = new PointsBill();
        bill.setUserId(userId);
        bill.setChangeType(changeType);
        bill.setPointsChange(pointsChange);
        bill.setBalanceBefore(balanceBefore);
        bill.setBalanceAfter(balanceAfter);
        bill.setBusinessId(businessId);
        bill.setDescription(description);
        bill.setModelName(modelName);
        bill.setExpireTime(expireTime);
        bill.setIsTemporary(isTemporary);
        bill.setCreateTime(createTime);
        
        pointsBillMapper.insert(bill);
        
        log.debug("点数账单记录: userId={}, type={}, change={}, balance={}->{}", 
                 userId, changeType, pointsChange, balanceBefore, balanceAfter);
    }

    /**
     * 获取用户点数账单列表
     * @param userId 用户ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 账单列表
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsBill> getPointsBillList(
            Long userId, int page, int pageSize, String changeType) {
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsBill> pageParam = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        
        LambdaQueryWrapper<PointsBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsBill::getUserId, userId);
        
        // 如果指定了变动类型，进行筛选
        if (changeType != null && !changeType.isEmpty()) {
            String normalizedType = changeType.trim().toUpperCase();
            if ("CONSUMPTION".equals(normalizedType)) {
                // 新结算链路写 SETTLE，兼容历史预扣记录 DEDUCT。
                wrapper.in(PointsBill::getChangeType, List.of("SETTLE", "DEDUCT"));
            } else if ("INCOME".equals(normalizedType)) {
                wrapper.in(PointsBill::getChangeType, List.of("RECHARGE", "SUBSCRIPTION_GRANT", "GRANT"));
            } else {
                wrapper.eq(PointsBill::getChangeType, normalizedType);
            }
        }
        
        wrapper.orderByDesc(PointsBill::getCreateTime);
        
        return pointsBillMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 每日免费点数发放
     * @param userId 用户ID
     * @param dailyFreePoints 每日免费点数
     * @return 发放后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal grantDailyFreePoints(Long userId, BigDecimal dailyFreePoints) {
        String grantKey = "daily_grant:" + userId + ":" + LocalDateTime.now().toLocalDate();
        
        // 检查今天是否已经发放
        Boolean exists = redisTemplate.hasKey(grantKey);
        if (Boolean.TRUE.equals(exists)) {
            log.info("今日已发放免费点数: userId={}", userId);
            return getUserPoints(userId);
        }
        
        // 发放免费点数
        String description = "每日免费点数发放";
        LocalDateTime expireTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        
        BigDecimal newBalance = addPoints(userId, dailyFreePoints, "GRANT", 
                                          "daily_" + LocalDateTime.now().toLocalDate(),
                description, expireTime, 1);
        
        // 标记今日已发放（24小时过期）
        redisTemplate.opsForValue().set(grantKey, "1", 25, java.util.concurrent.TimeUnit.HOURS);
        
        log.info("每日免费点数发放成功: userId={}, points={}, expireTime={}", 
                userId, dailyFreePoints, expireTime);
        
        return newBalance;
    }

    /**
     * 清理过期点数
     * @param userId 用户ID
     * @return 清理的点数
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal cleanExpiredPoints(Long userId) {
        return expireTemporaryPoints(userId, LocalDateTime.now());
    }

    /** Expire only the unused remainder of tracked temporary grants. */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal expireTemporaryPoints(Long userId, LocalDateTime now) {
        User lockedUser = lockUserPointsRow(userId);
        if (aiChargeIntentMapper.countActiveForUser(userId) > 0) {
            log.info("用户存在未完成 AI 点数请求，暂缓限时点数过期: userId={}", userId);
            return BigDecimal.ZERO;
        }

        List<PointsTemporaryLot> expiredLots = temporaryLotMapper.selectExpiredForUpdate(userId, now);
        if (expiredLots.isEmpty()) return BigDecimal.ZERO;

        ensurePointsCache(lockedUser);
        long databasePointsVersion = lockedUser.getPointsVersion() == null ? 0L : lockedUser.getPointsVersion();
        boolean updateDatabaseBalance = false;
        BigDecimal totalExpired = BigDecimal.ZERO;
        List<String> snapshotKeys = new ArrayList<>();

        for (PointsTemporaryLot lot : expiredLots) {
            BigDecimal amount = lot.getRemainingPoints().setScale(3, RoundingMode.HALF_DOWN);
            if (amount.signum() <= 0) {
                temporaryLotMapper.markDepleted(lot.getId());
                continue;
            }
            String snapshotKey = "points:expiration:" + lot.getId();
            LocalDateTime attemptedAt = now;
            List<Object> result = redisTemplate.execute(
                    temporaryExpiryScript,
                    List.of(USER_POINTS_PREFIX + userId, snapshotKey),
                    Long.toString(lot.getId()), amount.toPlainString(),
                    Long.toString(databasePointsVersion), attemptedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (result == null || result.isEmpty()) {
                throw new IllegalStateException("限时点数过期脚本返回为空: lotId=" + lot.getId());
            }
            long status = ((Number) result.get(0)).longValue();
            if (status != 1 && status != 2) {
                throw new IllegalStateException("限时点数过期脚本失败: lotId=" + lot.getId()
                        + ", reason=" + (result.size() > 1 ? result.get(1) : "unknown"));
            }
            if (result.size() < 5) {
                throw new IllegalStateException("限时点数过期快照缺少元数据: lotId=" + lot.getId());
            }

            BigDecimal snapshotBalance = new BigDecimal(result.get(1).toString())
                    .setScale(3, RoundingMode.HALF_DOWN);
            long snapshotVersion = Long.parseLong(result.get(2).toString());
            BigDecimal expiredAmount = new BigDecimal(result.get(3).toString())
                    .setScale(3, RoundingMode.HALF_DOWN);
            LocalDateTime expiredAt = LocalDateTime.parse(result.get(4).toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (databasePointsVersion < snapshotVersion) {
                throw new IllegalStateException("过期快照版本高于数据库版本: lotId=" + lot.getId());
            }
            if (databasePointsVersion == snapshotVersion) updateDatabaseBalance = true;

            recordBill(userId, "EXPIRE", expiredAmount.negate(), snapshotBalance.add(expiredAmount),
                    snapshotBalance, "expire_" + lot.getId(), "限时点数过期清理", null,
                    lot.getExpireTime(), 0, expiredAt);
            if (temporaryLotMapper.markExpired(lot.getId(), expiredAt) != 1) {
                throw new IllegalStateException("标记限时点数过期失败: lotId=" + lot.getId());
            }
            snapshotKeys.add(snapshotKey);
            totalExpired = totalExpired.add(expiredAmount);
        }

        if (totalExpired.signum() > 0 && updateDatabaseBalance) {
            String currentBalance = redisTemplate.opsForValue().get(USER_POINTS_PREFIX + userId);
            if (currentBalance == null) throw new IllegalStateException("过期后点数缓存丢失: userId=" + userId);
            updateUserPointsInDB(userId, new BigDecimal(currentBalance).setScale(3, RoundingMode.HALF_DOWN));
        }

        if (!snapshotKeys.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            List<String> keysToClean = List.copyOf(snapshotKeys);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.delete(keysToClean);
                }
            });
        }
        log.info("限时点数过期完成: userId={}, expired={}", userId, totalExpired);
        return totalExpired;
    }

    private void consumeTemporaryLots(Long userId, BigDecimal amount) {
        BigDecimal remainingToConsume = amount.setScale(3, RoundingMode.HALF_DOWN);
        if (remainingToConsume.signum() <= 0) return;
        for (PointsTemporaryLot lot : temporaryLotMapper.selectActiveForUpdate(userId)) {
            if (remainingToConsume.signum() <= 0) break;
            BigDecimal lotRemaining = lot.getRemainingPoints().setScale(3, RoundingMode.HALF_DOWN);
            BigDecimal consumed = lotRemaining.min(remainingToConsume);
            if (consumed.signum() <= 0) continue;
            if (temporaryLotMapper.consume(lot.getId(), consumed) != 1) {
                throw new IllegalStateException("分配限时点数消费失败: lotId=" + lot.getId());
            }
            if (lotRemaining.compareTo(consumed) == 0) temporaryLotMapper.markDepleted(lot.getId());
            remainingToConsume = remainingToConsume.subtract(consumed);
        }
    }

    /**
     * 格式化点数用于前端展示（保留两位小数）
     * @param points 点数（内部三位小数）
     * @return 格式化后的点数
     */
    public BigDecimal formatPointsForFrontend(BigDecimal points) {
        if (points == null) {
            return BigDecimal.ZERO;
        }
        return points.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 增加限时点数（每日发放，当日有效）
     *
     * @param userId      用户ID
     * @param points      点数
     * @param changeType  变动类型：DAILY_GRANT-每日发放
     * @param description 描述
     * @param expireTime  过期时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void addTemporaryPoints(Long userId, BigDecimal points, String changeType,
                                   String description, LocalDateTime expireTime) {
        addTemporaryPoints(userId, points, "temp_" + java.util.UUID.randomUUID(), changeType, description, expireTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addTemporaryPoints(Long userId, BigDecimal points, String businessId, String changeType,
                                   String description, LocalDateTime expireTime) {
        addPoints(userId, points, changeType,
                businessId,
                description, expireTime, 1);
    }

    /**
     * 增加永久点数（套餐赠送、充值等）
     *
     * @param userId      用户ID
     * @param points      点数
     * @param changeType  变动类型：SUBSCRIPTION_GRANT-套餐赠送, RECHARGE-充值
     * @param description 描述
     * @param businessId  业务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPermanentPoints(Long userId, BigDecimal points, String changeType,
                                   String description, String businessId) {
        addPoints(userId, points, changeType, businessId, description, null, 0);
    }

    /**
     * 获取用户点数详情（限时/永久拆分）
     *
     * @param userId 用户ID
     * @return 点数详情Map
     */
    public Map<String, Object> getUserPointsInfo(Long userId) {
        Map<String, Object> result = new HashMap<>();

        BigDecimal totalPoints = getUserPoints(userId);
        result.put("totalPoints", totalPoints);

        List<PointsTemporaryLot> activeLots = temporaryLotMapper.selectActiveForUser(userId);
        BigDecimal temporaryPoints = activeLots.stream()
                .map(PointsTemporaryLot::getRemainingPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal activeReservations = aiChargeIntentMapper.sumActiveReservationsForUser(userId);
        if (activeReservations != null) temporaryPoints = temporaryPoints.subtract(activeReservations);
        if (temporaryPoints.compareTo(BigDecimal.ZERO) < 0) temporaryPoints = BigDecimal.ZERO;
        result.put("temporaryPoints", temporaryPoints);

        BigDecimal permanentPoints = totalPoints.subtract(temporaryPoints);
        if (permanentPoints.compareTo(BigDecimal.ZERO) < 0) {
            permanentPoints = BigDecimal.ZERO;
        }
        result.put("permanentPoints", permanentPoints);

        LocalDateTime earliestExpire = activeLots.stream()
                .map(PointsTemporaryLot::getExpireTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        result.put("temporaryExpireTime", earliestExpire);

        return result;
    }

    /**
     * 获取用户点数消费汇总（今天/本月/总计）
     *
     * @param userId 用户ID
     * @return 消费汇总Map
     */
    public Map<String, Object> getConsumptionSummary(Long userId) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        // 今日消费
        LambdaQueryWrapper<PointsBill> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, List.of("SETTLE", "DEDUCT"))
                .lt(PointsBill::getPointsChange, BigDecimal.ZERO)
                .ge(PointsBill::getCreateTime, todayStart);
        List<PointsBill> todayBills = pointsBillMapper.selectList(todayWrapper);
        BigDecimal todayConsumed = todayBills.stream()
                .map(b -> b.getPointsChange().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本月消费
        LambdaQueryWrapper<PointsBill> monthWrapper = new LambdaQueryWrapper<>();
        monthWrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, List.of("SETTLE", "DEDUCT"))
                .lt(PointsBill::getPointsChange, BigDecimal.ZERO)
                .ge(PointsBill::getCreateTime, monthStart);
        List<PointsBill> monthBills = pointsBillMapper.selectList(monthWrapper);
        BigDecimal monthConsumed = monthBills.stream()
                .map(b -> b.getPointsChange().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 累计消费（所有Deduct记录）
        LambdaQueryWrapper<PointsBill> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(PointsBill::getUserId, userId)
                .in(PointsBill::getChangeType, List.of("SETTLE", "DEDUCT"))
                .lt(PointsBill::getPointsChange, BigDecimal.ZERO);
        List<PointsBill> totalBills = pointsBillMapper.selectList(totalWrapper);
        BigDecimal totalConsumed = totalBills.stream()
                .map(b -> b.getPointsChange().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 剩余点数
        BigDecimal remainingPoints = getUserPoints(userId);

        result.put("todayConsumed", todayConsumed.setScale(2, RoundingMode.HALF_UP));
        result.put("monthConsumed", monthConsumed.setScale(2, RoundingMode.HALF_UP));
        result.put("totalConsumed", totalConsumed.setScale(2, RoundingMode.HALF_UP));
        result.put("remainingPoints", remainingPoints.setScale(2, RoundingMode.HALF_UP));

        return result;
    }
}
