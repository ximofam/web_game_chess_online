-- KEYS[1] = room:{roomId}
-- KEYS[2] = presence:user:{userId}
-- KEYS[3] = room:{roomId}:spectators
-- ARGV[1] = userId
-- ARGV[2] = targetRole  (white | black | spectator)
-- ARGV[3] = timestamp (epoch ms, for spectator ZSet score)

local userId = ARGV[1]
local targetRole = ARGV[2]
local timestamp = tonumber(ARGV[3])

-- 1. Phòng phải tồn tại và đang WAITING
local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return Errors.ROOM_NOT_FOUND
end
if status ~= RoomStatus.WAITING then
    return Errors.ROOM_NOT_WAITING
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId')
local blackId = redis.call('HGET', KEYS[1], 'blackId')
local isSpectator = redis.call('ZSCORE', KEYS[3], userId) ~= false

-- 2. Xác định ghế hiện tại của user
local fromRole
if whiteId == userId then
    fromRole = PlayerRole.WHITE
elseif blackId == userId then
    fromRole = PlayerRole.BLACK
elseif isSpectator then
    fromRole = PlayerRole.SPECTATOR
else
    return Errors.NOT_IN_ROOM
end

-- 3. Không chuyển sang chính ghế đang ngồi
if fromRole == targetRole then
    return Errors.SEAT_SWITCH_NOT_ALLOWED
end

-- 4. Kiểm tra ghế đích có trống không (chỉ với player seat)
if targetRole == PlayerRole.WHITE and whiteId ~= nil and whiteId ~= '' then
    return Errors.SEAT_TAKEN
end
if targetRole == PlayerRole.BLACK and blackId ~= nil and blackId ~= '' then
    return Errors.SEAT_TAKEN
end

-- 5. Xoá khỏi ghế cũ
if fromRole == PlayerRole.WHITE then
    redis.call('HSET', KEYS[1], 'whiteId', '', 'whiteReady', 'false')
elseif fromRole == PlayerRole.BLACK then
    redis.call('HSET', KEYS[1], 'blackId', '', 'blackReady', 'false')
else
    redis.call('ZREM', KEYS[3], userId)
end

-- 6. Ngồi vào ghế mới
local roomId = redis.call('HGET', KEYS[1], 'roomId')
local isHost = redis.call('HGET', KEYS[2], 'is_host')

if targetRole == PlayerRole.WHITE then
    redis.call('HSET', KEYS[1], 'whiteId', userId)
    setUserPresenceToRoom(KEYS[2], roomId, isHost, PlayerRole.WHITE)
elseif targetRole == PlayerRole.BLACK then
    redis.call('HSET', KEYS[1], 'blackId', userId)
    setUserPresenceToRoom(KEYS[2], roomId, isHost, PlayerRole.BLACK)
else
    redis.call('ZADD', KEYS[3], timestamp, userId)
    if redis.call('ZCARD', KEYS[3]) == 1 then
        redis.call('EXPIRE', KEYS[3], 86400)
    end
    setUserPresenceToRoom(KEYS[2], roomId, isHost, PlayerRole.SPECTATOR)
end

return {OK, fromRole}
