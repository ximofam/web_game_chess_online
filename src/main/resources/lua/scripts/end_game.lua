-- KEYS[1] = room:{roomId}
-- KEYS[2] = game:{roomId}
-- KEYS[3] = game:{roomId}:moves

local status = redis.call('HGET', KEYS[1], 'status')
if not status then return {Errors.ROOM_NOT_FOUND} end
if status ~= RoomStatus.IN_PROGRESS then return {FAIL} end

local settingsRaw    = redis.call('HGET', KEYS[1], 'settings')
local whiteId        = redis.call('HGET', KEYS[2], 'whiteId')
local blackId        = redis.call('HGET', KEYS[2], 'blackId')
local startAt        = redis.call('HGET', KEYS[2], 'startAt')
local incrementMillis = redis.call('HGET', KEYS[2], 'incrementMillis')
local moves          = redis.call('LRANGE', KEYS[3], 0, -1)

redis.call('HSET', KEYS[1], 'status', RoomStatus.WAITING, 'whiteReady', 'false', 'blackReady', 'false')
redis.call('DEL', KEYS[2], KEYS[3])

-- return: {OK, settingsRaw, whiteId, blackId, startAt, incrementMillis, moves...}
local result = {OK, settingsRaw or "{}", whiteId, blackId, startAt, incrementMillis}
for _, move in ipairs(moves) do
    result[#result + 1] = move
end
return result
