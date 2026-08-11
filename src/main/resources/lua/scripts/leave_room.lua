-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:{roomId}:spectators
-- ARGV[1] = userId

local userId = ARGV[1]

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return { Errors.ROOM_NOT_FOUND }
end

-- Spectator leave: không cần kiểm tra status
local wasSpectator = false
if redis.call('ZSCORE', KEYS[2], userId) ~= false then
    redis.call('ZREM', KEYS[2], userId)
    local hostId = redis.call('HGET', KEYS[1], 'hostId') or ''
    if hostId ~= userId then
        return { OK, 'SPECTATOR_LEFT', PlayerRole.SPECTATOR }
    end
    wasSpectator = true
    -- Host là spectator → tiếp tục transfer bên dưới (ZSet đã xóa)
end

if not wasSpectator and status == RoomStatus.IN_PROGRESS then
    return { Errors.ROOM_IN_PROGRESS }
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId') or ''
local blackId = redis.call('HGET', KEYS[1], 'blackId') or ''
local hostId = redis.call('HGET', KEYS[1], 'hostId') or ''

-- Xác định role hiện tại của user
local role = (whiteId == userId and PlayerRole.WHITE) or (blackId == userId and PlayerRole.BLACK)
local isHost = hostId == userId

if not role and not isHost then
    return { Errors.NOT_IN_ROOM }
end

-- Xoá khỏi ghế nếu là player
if role then
    redis.call('HSET', KEYS[1], role .. 'Id', '', role .. 'Ready', 'false')
end

if status == RoomStatus.COUNTDOWN then
    redis.call('HSET', KEYS[1], 'status', RoomStatus.WAITING)
end

if not isHost then
    return { OK, 'PLAYER_LEFT', role, status }
end

-- Host leave: tìm người kế tiếp
-- Thứ tự ưu tiên: white → black → spectator vào sớm nhất (score nhỏ nhất)
-- Lưu ý: nếu host đang ngồi ghế white/black thì ghế đó đã được xóa ở trên
local newWhiteId = redis.call('HGET', KEYS[1], 'whiteId') or ''
local newBlackId = redis.call('HGET', KEYS[1], 'blackId') or ''
local nextHost = nil
local nextHostRole = nil

if newWhiteId ~= '' then
    nextHost = newWhiteId
    nextHostRole = PlayerRole.WHITE
elseif newBlackId ~= '' then
    nextHost = newBlackId
    nextHostRole = PlayerRole.BLACK
else
    -- Lấy spectator vào sớm nhất (score nhỏ nhất = ZADD timestamp nhỏ nhất)
    local earliest = redis.call('ZRANGE', KEYS[2], 0, 0)
    if #earliest > 0 then
        nextHost = earliest[1]
        nextHostRole = PlayerRole.SPECTATOR
        redis.call('ZREM', KEYS[2], nextHost)
    end
end

if nextHost then
    redis.call('HSET', KEYS[1], 'hostId', nextHost)
    return { OK, 'HOST_TRANSFERRED', role or PlayerRole.SPECTATOR, status, nextHost, nextHostRole }
end

-- Không còn ai → room cần xóa
redis.call('HSET', KEYS[1], 'hostId', '')
return { OK, 'ROOM_EMPTY', role or PlayerRole.SPECTATOR, status }