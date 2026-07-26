-- KEYS[1] = room:{roomId}
-- KEYS[2] = presence:user:{userId}
-- KEYS[3] = room:idx:lobby (LOBBY_INDEX)
-- KEYS[4] = room:{roomId}:spectators
-- ARGV[1] = userId
-- ARGV[2] = roomId

local userId = ARGV[1]
local roomId = ARGV[2]

-- 1. Phòng phải tồn tại và đang WAITING
local roomStatus = redis.call('HGET', KEYS[1], 'status')
if not roomStatus then
    return {-1, 'ROOM_NOT_FOUND', '', {}}
end
if roomStatus ~= 'WAITING' then
    return {-2, 'ROOM_NOT_WAITING', '', {}}
end

-- 2. Xác định user đang ở ghế nào (player) hay đứng xem (spectator)
local white = redis.call('HGET', KEYS[1], 'white') or ''
local black = redis.call('HGET', KEYS[1], 'black') or ''
local host  = redis.call('HGET', KEYS[1], 'host')  or ''

local isSpectator = redis.call('ZSCORE', KEYS[4], userId) ~= false

if white ~= userId and black ~= userId and host ~= userId and not isSpectator then
    return {-3, 'NOT_IN_ROOM', '', {}}
end

-- 3. Spectator rời: xóa khỏi ZSet, không cần đụng presence (spectator không set IN_ROOM)
if isSpectator then
    redis.call('ZREM', KEYS[4], userId)
    return {1, 'SPECTATOR_LEFT', 'spectator', {}}
end

local isHost = (host == userId)

if isHost then
    -- Host rời: lấy danh sách spectators để Java notify, rồi xóa toàn bộ room
    local spectators = redis.call('ZRANGE', KEYS[4], 0, -1)
    local others = {}
    if white ~= '' and white ~= userId then table.insert(others, white) end
    if black ~= '' and black ~= userId then table.insert(others, black) end
    -- Spectators không có presence IN_ROOM nên không cần reset, nhưng trả về để Java notify WS
    for _, sid in ipairs(spectators) do
        table.insert(others, 'spectator:' .. sid)
    end

    redis.call('DEL', KEYS[1])
    redis.call('ZREM', KEYS[3], roomId)
    redis.call('DEL', KEYS[2])
    redis.call('DEL', KEYS[4])
    return {1, 'HOST_LEFT', 'host', others}
else
    -- Non-host player rời: clear ghế, reset presence về ONLINE
    local role = (white == userId) and 'white' or 'black'
    redis.call('HSET', KEYS[1], role, '')
    redis.call('DEL', KEYS[2])
    return {1, 'PLAYER_LEFT', role, {}}
end
