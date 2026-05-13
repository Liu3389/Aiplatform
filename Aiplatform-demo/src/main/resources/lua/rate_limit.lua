-- 限流Lua脚本（令牌桶算法）
-- KEYS[1]: 限流Key (ai_gateway:rate_limit:{apiKey})
-- ARGV[1]: 限流阈值（每分钟请求数）
-- ARGV[2]: 当前时间戳（秒）
-- ARGV[3]: 时间窗口（秒，通常为60）

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local window = tonumber(ARGV[3])

-- 获取当前计数
local current = redis.call('GET', key)

if current == false then
    -- 第一次请求，初始化计数器
    redis.call('SET', key, 1, 'EX', window)
    return 1
else
    current = tonumber(current)
    if current < limit then
        -- 未超过限制，增加计数
        redis.call('INCR', key)
        return 1
    else
        -- 超过限制，拒绝请求
        return 0
    end
end
