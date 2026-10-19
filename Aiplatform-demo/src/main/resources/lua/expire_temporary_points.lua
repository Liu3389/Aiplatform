-- Idempotently remove one expired temporary points lot from the aggregate balance.
-- KEYS[1]: user points balance
-- KEYS[2]: expiration replay snapshot for this lot
-- ARGV[1]: lot ID
-- ARGV[2]: remaining points in this lot
-- ARGV[3]: database points version before this expiration
-- ARGV[4]: expiration timestamp (ISO_LOCAL_DATE_TIME)

local pointsKey = KEYS[1]
local snapshotKey = KEYS[2]
local lotId = ARGV[1]
local amount = tonumber(ARGV[2])
local databasePointsVersion = ARGV[3]
local expiredAt = ARGV[4]

local savedLotId = redis.call('HGET', snapshotKey, 'lotId')
if savedLotId then
    local savedAmount = tonumber(redis.call('HGET', snapshotKey, 'amount'))
    if savedLotId ~= lotId or savedAmount ~= amount then
        return {-1, 'expiration_snapshot_conflict'}
    end
    local savedVersion = redis.call('HGET', snapshotKey, 'databasePointsVersion')
    local savedBalance = redis.call('HGET', snapshotKey, 'balanceAfter')
    local savedTime = redis.call('HGET', snapshotKey, 'expiredAt')
    if not savedVersion or not savedBalance or not savedTime then
        return {-2, 'expiration_snapshot_metadata_missing'}
    end
    if databasePointsVersion == savedVersion then
        redis.call('SET', pointsKey, savedBalance)
    end
    return {2, savedBalance, savedVersion, savedAmount, savedTime}
end

local currentPointsStr = redis.call('GET', pointsKey)
if not currentPointsStr then
    return {0, 'balance_cache_missing'}
end
local currentPoints = tonumber(currentPointsStr)
if currentPoints < amount then
    return {0, 'temporary_balance_exceeds_wallet'}
end

local balanceAfter = string.format('%.3f', currentPoints - amount)
redis.call('SET', pointsKey, balanceAfter)
redis.call('HSET', snapshotKey,
        'lotId', lotId,
        'amount', string.format('%.3f', amount),
        'balanceAfter', balanceAfter,
        'databasePointsVersion', databasePointsVersion,
        'expiredAt', expiredAt)
return {1, balanceAfter, databasePointsVersion, string.format('%.3f', amount), expiredAt}
