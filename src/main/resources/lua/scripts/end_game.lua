-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:{roomId}:game
-- KEYS[3] = room:{roomId}:game:moves

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return { Errors.ROOM_NOT_FOUND }
end
if status ~= RoomStatus.IN_PROGRESS then
    return { FAIL }
end

local settingsRaw = redis.call('HGET', KEYS[1], 'settings')
local whiteId = redis.call('HGET', KEYS[2], 'whiteId')
local blackId = redis.call('HGET', KEYS[2], 'blackId')
local startAt = redis.call('HGET', KEYS[2], 'startAt')
local incrementMillis = redis.call('HGET', KEYS[2], 'incrementMillis')
local moves = redis.call('LRANGE', KEYS[3], 0, -1)

redis.call('HSET', KEYS[1], 'status', RoomStatus.WAITING, 'whiteReady', 'false', 'blackReady', 'false')
redis.call('DEL', KEYS[2], KEYS[3])

local whitePresenceKey = buildPresenceKey(whiteId)
local blackPresenceKey = buildPresenceKey(blackId)
local whiteStatus = redis.call('HGET', whitePresenceKey, 'status')
local blackStatus = redis.call('HGET', blackPresenceKey, 'status')
if whiteStatus == UserStatus.PLAYING then
    redis.call('HSET', whitePresenceKey, 'status', UserStatus.IN_ROOM)
end
if blackStatus == UserStatus.PLAYING then
    redis.call('HSET', blackPresenceKey, 'status', UserStatus.IN_ROOM)
end

-- return: {OK, settingsRaw, whiteId, blackId, startAt, incrementMillis, moves...}
local result = { OK, settingsRaw or "{}", whiteId, blackId, startAt, incrementMillis }
for _, move in ipairs(moves) do
    result[#result + 1] = move
end

return result
