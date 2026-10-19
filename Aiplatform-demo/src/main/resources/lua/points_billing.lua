-- 点数计费 Lua 脚本（预扣、实扣、回滚原子性保证）
-- KEYS[1]: 用户点数 Key (ai_gateway:user_points:{userId})
-- KEYS[2]: 预扣记录 Key (ai_gateway:points_pre_deduct:{requestId})
-- KEYS[3]: 退款幂等标记 Key
-- KEYS[4]: 结算幂等标记 Key
-- ARGV[1]: 用户 ID
-- ARGV[2]: 预扣点数（字符串格式，如"3.000"）
-- ARGV[3]: 请求 ID（用于幂等性）
-- ARGV[4]: 过期时间（秒）
-- ARGV[5]: Redis 预扣 Key 丢失时的持久化预扣金额
-- ARGV[6]: 当前数据库点数版本
-- ARGV[7]: 当前数据库点数余额（仅缓存缺失恢复使用）

local pointsKey = KEYS[1]
local preDeductKey = KEYS[2]
local userId = ARGV[1]
local preDeductPointsStr = ARGV[2]
local requestId = ARGV[3]
local expireTime = tonumber(ARGV[4])

-- ARGV[2]=0 表示回滚。必须放在幂等预扣判断之前，否则已存在的预扣标记
-- 会直接返回成功但不会退回点数，导致用户在失败/断线后永久丢点。
if tonumber(preDeductPointsStr) == 0 then
    local refundKey = KEYS[3]
    local settledKey = KEYS[4]
    local fallbackPoints = tonumber(ARGV[5] or "0")
    local databasePointsVersion = ARGV[6] or "0"
    local databaseBalance = ARGV[7] or "0"

    if redis.call('HGET', settledKey, 'requestId') then
        return {-1, 'already_settled'}
    end
    local refundedRequestId = redis.call('HGET', refundKey, 'requestId')
    if refundedRequestId then
        if refundedRequestId ~= requestId then return {-2, 'request_id_conflict'} end
        local savedVersion = redis.call('HGET', refundKey, 'databasePointsVersion') or "0"
        local savedBalance = redis.call('HGET', refundKey, 'balanceAfter') or databaseBalance
        if redis.call('GET', pointsKey) == false then
            redis.call('SET', pointsKey, databasePointsVersion == savedVersion and savedBalance or databaseBalance)
        end
        return {2,
                redis.call('GET', pointsKey) or "0",
                savedVersion,
                redis.call('HGET', refundKey, 'amount') or "0"}
    end

    local existingPreDeduct = redis.call('GET', preDeductKey)
    local refundPoints = fallbackPoints
    if existingPreDeduct ~= false then
        refundPoints = tonumber(existingPreDeduct)
    end

    -- MySQL is authoritative when the Redis balance key itself is absent. A missing
    -- cache cannot prove that an uncommitted reservation is still reflected in Redis,
    -- so restore the DB balance and atomically close the refund intent without crediting twice.
    if redis.call('GET', pointsKey) == false then
        if existingPreDeduct == false and fallbackPoints <= 0 then
            return {0, databaseBalance}
        end
        redis.call('SET', pointsKey, databaseBalance)
        redis.call('DEL', preDeductKey)
        redis.call('HSET', refundKey,
                'requestId', requestId,
                'amount', "0.000",
                'balanceAfter', databaseBalance,
                'databasePointsVersion', databasePointsVersion)
        redis.call('EXPIRE', refundKey, 2592000)
        return {3, databaseBalance, databasePointsVersion, "0.000"}
    end

    if refundPoints <= 0 then
        return {0, redis.call('GET', pointsKey) or "0"}
    end

    local currentPointsStr = redis.call('GET', pointsKey)
    local currentPoints = tonumber(currentPointsStr or "0")
    local newPoints = string.format("%.3f", currentPoints + refundPoints)
    redis.call('SET', pointsKey, newPoints)
    redis.call('DEL', preDeductKey)
    redis.call('HSET', refundKey,
            'requestId', requestId,
            'amount', string.format("%.3f", refundPoints),
            'balanceAfter', newPoints,
            'databasePointsVersion', databasePointsVersion)
    redis.call('EXPIRE', refundKey, 2592000)
    return {1, newPoints, databasePointsVersion, string.format("%.3f", refundPoints)}
end

-- 检查是否已经预扣过（幂等性）
local existingPreDeduct = redis.call('GET', preDeductKey)
if existingPreDeduct ~= false then
    -- 已经预扣过，返回成功和当前点数
    return {1, redis.call('GET', pointsKey)}
end

-- 获取当前点数
local currentPointsStr = redis.call('GET', pointsKey)
if currentPointsStr == false then
    -- 点数不存在，视为 0
    return {0, "0"}
end

-- 将字符串转换为数字进行比较
local currentPoints = tonumber(currentPointsStr)
local preDeductPoints = tonumber(preDeductPointsStr)

-- 检查点数是否充足
if currentPoints < preDeductPoints then
    -- 点数不足
    return {0, currentPointsStr}
end

-- 执行预扣（保留3位小数精度）
local newPoints = currentPoints - preDeductPoints
newPoints = string.format("%.3f", newPoints)
redis.call('SET', pointsKey, newPoints)

-- 持久化 intent 负责恢复，不能让一个 Redis TTL 丢掉用户的预扣证据。
if expireTime and expireTime > 0 then
    redis.call('SET', preDeductKey, preDeductPointsStr, 'EX', expireTime)
else
    redis.call('SET', preDeductKey, preDeductPointsStr)
end

-- 返回成功和新点数
return {1, newPoints}
