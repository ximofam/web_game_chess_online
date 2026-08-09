-- KEYS[1] = room:{roomId}
-- ARGV[1] = userId
-- ARGV[2] = isReady ("true" or "false")
-- ARGV[3] = startAt (timestamp, used to start countdown if both are ready)

local COUNTDOWN_STARTED = 2
local COUNTDOWN_CANCELLED = 3

local userId = ARGV[1]
local isReady = ARGV[2]
local startAt = tonumber(ARGV[3])

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return { Errors.ROOM_NOT_FOUND }
end
if status ~= RoomStatus.WAITING and status ~= RoomStatus.COUNTDOWN then
    return { Errors.ROOM_NOT_WAITING }
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId')
local blackId = redis.call('HGET', KEYS[1], 'blackId')

local role = nil
if whiteId == userId then
    role = 'white'
elseif blackId == userId then
    role = 'black'
else
    return { Errors.NOT_A_PLAYER }
end

redis.call('HSET', KEYS[1], role .. 'Ready', isReady)

if isReady == 'true' then
    if status == RoomStatus.WAITING then
        local wReady = redis.call('HGET', KEYS[1], 'whiteReady')
        local bReady = redis.call('HGET', KEYS[1], 'blackReady')

        if wReady == 'true' and bReady == 'true' then
            redis.call('HMSET', KEYS[1], 'status', RoomStatus.COUNTDOWN, 'startAt', startAt)
            return { COUNTDOWN_STARTED, role }
        end
    end
else
    if status == RoomStatus.COUNTDOWN then
        redis.call('HMSET', KEYS[1], 'status', RoomStatus.WAITING, 'startAt', '')
        return { COUNTDOWN_CANCELLED, role }
    end
end

return { OK, role }
