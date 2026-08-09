-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:{roomId}:spectators
-- ARGV[1] = userId

local userId = ARGV[1]

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return { Errors.ROOM_NOT_FOUND }
end

if redis.call('ZSCORE', KEYS[2], userId) ~= false then
    redis.call('ZREM', KEYS[2], userId)
    return { OK, 'SPECTATOR_LEFT', PlayerRole.SPECTATOR }
end

if status == RoomStatus.IN_PROGRESS then
    return { Errors.ROOM_IN_PROGRESS }
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId') or ''
local blackId = redis.call('HGET', KEYS[1], 'blackId') or ''
local hostId = redis.call('HGET', KEYS[1], 'hostId') or ''
local role = (whiteId == userId and PlayerRole.WHITE) or (blackId == userId and PlayerRole.BLACK)

if role then
    redis.call('HSET', KEYS[1], role .. 'Id', '', role .. 'Ready', 'false')
end

if status == RoomStatus.COUNTDOWN then
    redis.call('HSET', KEYS[1], 'status', RoomStatus.WAITING)
end

if hostId == userId then
    redis.call('HSET', KEYS[1], 'hostId', '')
    return { OK, 'HOST_LEFT', 'host', status }
end

if role == PlayerRole.WHITE or role == PlayerRole.BLACK then
    return { OK, 'PLAYER_LEFT', role, status }
end

return { Errors.NOT_IN_ROOM }