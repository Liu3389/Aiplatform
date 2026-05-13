-- 结算Lua脚本（实际扣费，退还多余预扣金额）
-- KEYS[1]: 用户余额Key (ai_gateway:user_balance:{userId})
-- KEYS[2]: 预扣记录Key (ai_gateway:billing_pre_deduct:{requestId})
-- ARGV[1]: 用户ID
-- ARGV[2]: 实际消费金额（字符串格式）
-- ARGV[3]: 请求ID

local balanceKey = KEYS[1]
local preDeductKey = KEYS[2]
local userId = ARGV[1]
local actualCostStr = ARGV[2]
local requestId = ARGV[3]

-- 获取预扣金额
local preDeductAmountStr = redis.call('GET', preDeductKey)
if preDeductAmountStr == false then
    -- 预扣记录不存在，无法结算
    return {0, "0", "0"}
end

-- 获取当前余额
local currentBalanceStr = redis.call('GET', balanceKey)
if currentBalanceStr == false then
    currentBalanceStr = "0"
end

-- 转换为数字进行计算
local preDeductAmount = tonumber(preDeductAmountStr)
local currentBalance = tonumber(currentBalanceStr)
local actualCost = tonumber(actualCostStr)

-- 计算差额（预扣金额 - 实际消费金额）
local difference = preDeductAmount - actualCost

-- 退还差额到余额
local newBalance = currentBalance + difference
-- 保留6位小数，避免浮点数精度问题
newBalance = string.format("%.6f", newBalance)
redis.call('SET', balanceKey, newBalance)

-- 删除预扣记录
redis.call('DEL', preDeductKey)

-- 返回成功、新余额和退还金额
return {1, newBalance, tostring(difference)}
