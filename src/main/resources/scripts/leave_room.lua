-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:{roomId}:spectators
-- ARGV[1] = userId

local userId = ARGV[1]

-- Result codes
local OK = 1

local ERR_ROOM_NOT_FOUND = -1
local ERR_ROOM_NOT_WAITING = -2
local ERR_NOT_IN_ROOM = -3

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return {ERR_ROOM_NOT_FOUND, 'ROOM_NOT_FOUND', ''}
end

if status ~= 'WAITING' then
    return {ERR_ROOM_NOT_WAITING, 'ROOM_NOT_WAITING', ''}
end

if redis.call('ZSCORE', KEYS[2], userId) ~= false then
    redis.call('ZREM', KEYS[2], userId)
    return {OK, 'SPECTATOR_LEFT', 'spectator'}
end

local white = redis.call('HGET', KEYS[1], 'white') or ''
local black = redis.call('HGET', KEYS[1], 'black') or ''
local host  = redis.call('HGET', KEYS[1], 'host') or ''
local role = (white == userId and 'white') or (black == userId and 'black')

if role then
    redis.call('HSET', KEYS[1], role, '')
end

if host == userId then
    redis.call('HSET', KEYS[1], 'host', '')
    return {OK, 'HOST_LEFT', 'host'}
end

if role then
    return {OK, 'PLAYER_LEFT', role}
end

return {ERR_NOT_IN_ROOM, 'NOT_IN_ROOM', ''}