-- 免费Token增加 (Lua 脚本)
-- KEYS[1]: token_key
-- ARGV[1]: tokens_to_add
-- ARGV[2]: expire_seconds

local current = redis.call('GET', KEYS[1])
if current == false then
    current = 0
else
    current = tonumber(current)
end

local newTotal = current + tonumber(ARGV[1])
redis.call('SET', KEYS[1], tostring(newTotal))
redis.call('EXPIRE', KEYS[1], ARGV[2])

return newTotal
