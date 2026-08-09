-- KEYS[1] = user:{userId}:presence
-- KEYS[2] = room:{roomId}:game
-- ARGV[1] = role (white/black)
-- ARGV[2] = currentTimeMs

local presenceKey = KEYS[1]
local gameKey = KEYS[2]
local role = ARGV[1]
local currentTimeMs = tonumber(ARGV[2])

if redis.call('EXISTS', gameKey) == 0 then
    redis.call('DEL', presenceKey)
    return 0
end

local turn = redis.call('HGET', gameKey, 'turn')
local remainingMillis = tonumber(redis.call('HGET', gameKey, role .. 'RemainingMillis'))
local turnStartedAt = tonumber(redis.call('HGET', gameKey, 'turnStartedAt'))

if remainingMillis then
    local ttl = remainingMillis
    if turn and turn == role and turnStartedAt then
        if currentTimeMs then
            ttl = ttl - (currentTimeMs - turnStartedAt)
        end
    end

    if ttl > 0 then
        redis.call('PEXPIRE', presenceKey, ttl)
        return 1
    end
end

redis.call('DEL', presenceKey)
return 0
