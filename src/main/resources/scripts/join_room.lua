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

-- 3. Đọc settings (parse 1 lần dùng chung)
local settingsRaw = redis.call('HGET', KEYS[1], 'settings')
local settings = {}
if settingsRaw then
    local ok, decoded = pcall(cjson.decode, settingsRaw)
    if ok then settings = decoded end
end

-- 4. Nếu phòng private thì chặn join (bất kể role gì)
if settings.private == true or settings.isPrivate == true then
    return {-7, 'ROOM_IS_PRIVATE'}
end

-- 5. User không được là player đang ngồi sẵn (phòng tránh chuyển ghế tự do phá logic)
local white = redis.call('HGET', KEYS[1], 'white')
local black = redis.call('HGET', KEYS[1], 'black')

if white == userId or black == userId then
    return {-3, 'ALREADY_SEATED'}
end

-- 6. Xử lý theo role
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
    if settings.spectatorLocked then
        return {-5, 'SPECTATORS_LOCKED'}
    end
    redis.call('ZADD', KEYS[3], timestamp, userId)
    redis.call('HSET', KEYS[2], 'status', 'IN_ROOM', 'roomId', roomId, 'is_host', 'false', 'role', 'spectator')
    return {1, 'OK'}

else
    return {-6, 'INVALID_ROLE'}
end
