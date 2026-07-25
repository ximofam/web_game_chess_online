-- KEYS[1] = room:{roomId}
-- KEYS[2] = presence:user:{userId}
-- KEYS[3] = room:{roomId}:spectators
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = role  (white | black | spectator)
-- ARGV[4] = timestamp (epoch ms, for spectator ZSet score)

local userId    = ARGV[1]
local roomId    = ARGV[2]
local role      = ARGV[3]
local timestamp = tonumber(ARGV[4])

-- 1. Phòng phải tồn tại
local roomStatus = redis.call('HGET', KEYS[1], 'status')
if not roomStatus then
    return {-1, 'ROOM_NOT_FOUND'}
end

-- 2. Phòng phải đang WAITING
if roomStatus ~= 'WAITING' then
    return {-2, 'ROOM_NOT_WAITING'}
end

-- 3. User không được là player đang ngồi sẵn (phòng tránh chuyển ghế tự do phá logic)
local white = redis.call('HGET', KEYS[1], 'white')
local black = redis.call('HGET', KEYS[1], 'black')

if white == userId or black == userId then
    return {-3, 'ALREADY_SEATED'}
end

-- 4. Xử lý theo role
if role == 'white' then
    if white ~= nil and white ~= '' then
        return {-4, 'SEAT_TAKEN'}
    end
    redis.call('HSET', KEYS[1], 'white', userId)
    redis.call('HSET', KEYS[2], 'status', 'IN_ROOM', 'roomId', roomId, 'is_host', 'false', 'role', 'white')
    return {1, 'OK'}

elseif role == 'black' then
    if black ~= nil and black ~= '' then
        return {-4, 'SEAT_TAKEN'}
    end
    redis.call('HSET', KEYS[1], 'black', userId)
    redis.call('HSET', KEYS[2], 'status', 'IN_ROOM', 'roomId', roomId, 'is_host', 'false', 'role', 'black')
    return {1, 'OK'}

elseif role == 'spectator' then
    local settingsRaw = redis.call('HGET', KEYS[1], 'settings')
    if settingsRaw then
        local ok, settings = pcall(cjson.decode, settingsRaw)
        if ok and settings.spectatorLocked then
            return {-5, 'SPECTATORS_LOCKED'}
        end
    end
    redis.call('ZADD', KEYS[3], timestamp, userId)
    return {1, 'OK'}

else
    return {-6, 'INVALID_ROLE'}
end
