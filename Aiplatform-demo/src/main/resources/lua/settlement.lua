-- 原子结算脚本
-- 解决 settlePoints 方法中 读-读-计算-写 四步非原子操作导致的并发余额不一致问题
-- KEYS[1]: 用户点数 key
-- KEYS[2]: 本次请求的预扣记录 key
-- KEYS[3]: 本次请求的结算幂等标记 key
-- KEYS[4]: 本次请求的退款幂等标记 key
-- ARGV[1]: 实际消耗点数
-- ARGV[2]: requestId
-- ARGV[3]: 本次结算前的数据库点数版本
-- ARGV[4]: 本次结算时间（ISO_LOCAL_DATE_TIME）
-- ARGV[5]: Redis 预扣 Key 丢失时的持久化预扣金额
-- 返回: {1|2, 结算后余额, 预扣金额, 数据库点数版本, 结算时间}；1=首次结算，2=幂等重放

local pointsKey = KEYS[1]
local preDeductKey = KEYS[2]
local settledKey = KEYS[3]
local actualConsumed = tonumber(ARGV[1])
local requestId = ARGV[2]
local databasePointsVersion = ARGV[3]
local settledAt = ARGV[4]
local fallbackPreDeduct = tonumber(ARGV[5] or "0")

if redis.call('HGET', KEYS[4], 'requestId') then
    return {-1, 'already_refunded'}
end

-- Redis 操作与 MySQL 落账之间可能发生进程崩溃。保留结算结果，
-- 让重试能用同一 requestId 补写数据库流水，而不会再次改余额。
local settledRequestId = redis.call('HGET', settledKey, 'requestId')
if settledRequestId then
    local settledActual = tonumber(redis.call('HGET', settledKey, 'actual'))
    if settledRequestId ~= requestId or settledActual ~= actualConsumed then
        return {-1, 'request_id_conflict'}
    end
    local savedVersion = redis.call('HGET', settledKey, 'databasePointsVersion')
    local savedSettledAt = redis.call('HGET', settledKey, 'settledAt')
    if not savedVersion or not savedSettledAt then
        -- Legacy snapshots have no ordering metadata. Replaying them could roll back a newer DB balance.
        return {-2, 'legacy_settlement_metadata_missing'}
    end
    -- If the DB transaction failed, its version is unchanged; restore the Redis mirror
    -- from the durable settlement snapshot while the caller holds the user's DB row lock.
    if ARGV[3] == savedVersion then
        redis.call('SET', pointsKey, redis.call('HGET', settledKey, 'balanceAfter'))
    end
    return {2,
            redis.call('HGET', settledKey, 'balanceAfter'),
            redis.call('HGET', settledKey, 'preDeduct'),
            savedVersion,
            savedSettledAt}
end

-- 读取预扣记录
local preDeductAmount = redis.call('GET', preDeductKey)
if not preDeductAmount then
    if fallbackPreDeduct <= 0 then return {0, 'no_pre_deduct'} end
    preDeductAmount = ARGV[5]
end

local preDeducted = tonumber(preDeductAmount)

-- 读取当前余额
local currentPointsStr = redis.call('GET', pointsKey)
local currentPoints = 0
if currentPointsStr then
    currentPoints = tonumber(currentPointsStr)
end

-- 计算差值: 预扣 - 实际 = 应退还(正数) 或 应补扣(负数)
local difference = preDeducted - actualConsumed
local balanceAfter = currentPoints + difference

-- 写回余额
redis.call('SET', pointsKey, tostring(balanceAfter))

redis.call('HSET', settledKey,
        'requestId', requestId,
        'actual', tostring(actualConsumed),
        'preDeduct', tostring(preDeducted),
        'balanceAfter', tostring(balanceAfter),
        'databasePointsVersion', databasePointsVersion,
        'settledAt', settledAt)
-- 删除预扣记录
redis.call('DEL', preDeductKey)

return {1, tostring(balanceAfter), tostring(preDeducted), databasePointsVersion, settledAt}
