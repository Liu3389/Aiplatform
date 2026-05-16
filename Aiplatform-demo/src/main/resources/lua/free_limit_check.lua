-- 免费配额检查与记录 (Lua 脚本)
-- KEYS[1]: call_count_key
-- ARGV[1]: max_calls
-- ARGV[2]: expire_seconds

local count = redis.call('GET', KEYS[1])
if count == false then
    count = 0
else
    count = tonumber(count)
end

local maxCalls = tonumber(ARGV[1])
if count >= maxCalls then
    return 0 -- 超出限额
end

redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1 -- 成功
