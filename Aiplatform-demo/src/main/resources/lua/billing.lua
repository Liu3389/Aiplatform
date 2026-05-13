-- 计费Lua脚本（预扣余额+原子性保证）
-- KEYS[1]: 用户余额Key (ai_gateway:user_balance:{userId})
-- KEYS[2]: 预扣记录Key (ai_gateway:billing_pre_deduct:{requestId})
-- ARGV[1]: 用户ID
-- ARGV[2]: 预扣金额（字符串格式，如"0.1"）
-- ARGV[3]: 请求ID（用于幂等性）
-- ARGV[4]: 过期时间（秒）

local balanceKey = KEYS[1]
local preDeductKey = KEYS[2]
local userId = ARGV[1]
local preDeductAmountStr = ARGV[2]
local requestId = ARGV[3]
local expireTime = tonumber(ARGV[4])

-- 检查是否已经预扣过（幂等性）
local existingPreDeduct = redis.call('GET', preDeductKey)
if existingPreDeduct ~= false then
    -- 已经预扣过，返回成功
    return {1, redis.call('GET', balanceKey)}
end

-- 获取当前余额
local currentBalanceStr = redis.call('GET', balanceKey)
if currentBalanceStr == false then
    -- 余额不存在，视为0
    return {0, "0"}
end

-- 将字符串转换为数字进行比较（使用高精度）
local currentBalance = tonumber(currentBalanceStr)
local preDeductAmount = tonumber(preDeductAmountStr)

-- 检查余额是否充足
if currentBalance < preDeductAmount then
    -- 余额不足
    return {0, currentBalanceStr}
end

-- 执行预扣（使用字符串运算保持精度）
local newBalance = currentBalance - preDeductAmount
-- 保留6位小数，避免浮点数精度问题
newBalance = string.format("%.6f", newBalance)
redis.call('SET', balanceKey, newBalance)

-- 记录预扣信息（用于后续结算或回滚）
redis.call('SET', preDeductKey, preDeductAmountStr, 'EX', expireTime)

-- 返回成功和新余额
return {1, newBalance}
