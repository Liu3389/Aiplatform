-- 点数计费 Lua 脚本（预扣、实扣、回滚原子性保证）
-- KEYS[1]: 用户点数 Key (ai_gateway:user_points:{userId})
-- KEYS[2]: 预扣记录 Key (ai_gateway:points_pre_deduct:{requestId})
-- ARGV[1]: 用户 ID
-- ARGV[2]: 预扣点数（字符串格式，如"3.000"）
-- ARGV[3]: 请求 ID（用于幂等性）
-- ARGV[4]: 过期时间（秒）

local pointsKey = KEYS[1]
local preDeductKey = KEYS[2]
local userId = ARGV[1]
local preDeductPointsStr = ARGV[2]
local requestId = ARGV[3]
local expireTime = tonumber(ARGV[4])

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

-- 记录预扣信息（用于后续结算或回滚）
redis.call('SET', preDeductKey, preDeductPointsStr, 'EX', expireTime)

-- 返回成功和新点数
return {1, newPoints}
