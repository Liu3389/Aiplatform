-- 原子结算脚本
-- 解决 settlePoints 方法中 读-读-计算-写 四步非原子操作导致的并发余额不一致问题
-- KEYS[1]: 用户点数 key (ai_gateway:points:{userId})
-- KEYS[2]: 预扣记录 key (ai_gateway:pre_deduct:{userId}:{sessionId})
-- ARGV[1]: 实际消耗点数
-- 返回: {1, 结算后余额} 成功 | {0, 'no_pre_deduct'} 无预扣记录 | {-1, error_msg} 异常

local pointsKey = KEYS[1]
local preDeductKey = KEYS[2]
local actualConsumed = tonumber(ARGV[1])

-- 读取预扣记录
local preDeductAmount = redis.call('GET', preDeductKey)
if not preDeductAmount then
    return {0, 'no_pre_deduct'}
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

-- 删除预扣记录
redis.call('DEL', preDeductKey)

return {1, tostring(balanceAfter)}
