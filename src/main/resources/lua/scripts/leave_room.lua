-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:{roomId}:spectators
-- ARGV[1] = userId

local userId = ARGV[1]

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return {Errors.ROOM_NOT_FOUND, nil, nil}
end

if redis.call('ZSCORE', KEYS[2], userId) ~= false then
    redis.call('ZREM', KEYS[2], userId)
    return {OK, 'SPECTATOR_LEFT', 'spectator'}
end

if status ~= RoomStatus.WAITING then
    return {Errors.ROOM_NOT_WAITING, nil, nil}
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId') or ''
local blackId = redis.call('HGET', KEYS[1], 'blackId') or ''
local hostId  = redis.call('HGET', KEYS[1], 'hostId') or ''
local role = (whiteId == userId and 'white') or (blackId == userId and 'black')

if role then
    redis.call('HSET', KEYS[1], role .. 'Id', '', role .. 'Ready', 'false')
end

if hostId == userId then
    redis.call('HSET', KEYS[1], 'hostId', '')
    return {OK, 'HOST_LEFT', 'host'}
end

if role == 'white' or role == 'black' then
    return {OK, 'PLAYER_LEFT', role}
end

return {Errors.NOT_IN_ROOM, nil, nil}