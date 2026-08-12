-- KEYS[1] = room:{roomId}
-- KEYS[2] = presence:user:{userId}
-- KEYS[3] = room:{roomId}:spectators
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = role  (white | black | spectator)
-- ARGV[4] = timestamp (epoch ms, for spectator ZSet score)

local userId = ARGV[1]
local roomId = ARGV[2]
local role = ARGV[3]
local timestamp = tonumber(ARGV[4])

local err = checkUserStatusOnline(KEYS[2])
if err then
    return err
end

-- 1. Phòng phải tồn tại
local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return Errors.ROOM_NOT_FOUND
end

-- 2. Phòng phải đang WAITING
if status ~= RoomStatus.WAITING and role ~= PlayerRole.SPECTATOR then
    return Errors.ROOM_NOT_WAITING
end

-- 3. Đọc settings (parse 1 lần dùng chung)
local settingsRaw = redis.call('HGET', KEYS[1], 'settings')
local settings = {}
if settingsRaw then
    local ok, decoded = pcall(cjson.decode, settingsRaw)
    if ok then
        settings = decoded
    end
end

-- 4. Nếu phòng private thì chặn join (bất kể role gì)
if settings.isPrivate then
    return Errors.ROOM_IS_PRIVATE
end

-- 5. User không được là player đang ngồi sẵn (phòng tránh chuyển ghế tự do phá logic)
local whiteId = redis.call('HGET', KEYS[1], 'whiteId')
local blackId = redis.call('HGET', KEYS[1], 'blackId')
local hostId = redis.call('HGET', KEYS[1], 'hostId')

if whiteId == userId or blackId == userId or hostId == userId or redis.call('ZSCORE', KEYS[3], userId) then
    return Errors.ALREADY_SEATED
end

-- 6. Xử lý theo role
if role == PlayerRole.WHITE then
    if whiteId ~= nil and whiteId ~= '' then
        return Errors.SEAT_TAKEN
    end
    redis.call('HSET', KEYS[1], 'whiteId', userId)
    setUserPresenceToRoom(KEYS[2], roomId, 'false', PlayerRole.WHITE)
    return OK

elseif role == PlayerRole.BLACK then
    if blackId ~= nil and blackId ~= '' then
        return Errors.SEAT_TAKEN
    end
    redis.call('HSET', KEYS[1], 'blackId', userId)
    setUserPresenceToRoom(KEYS[2], roomId, 'false', PlayerRole.BLACK)
    return OK

elseif role == PlayerRole.SPECTATOR then
    if settings.spectatorLocked then
        return Errors.SPECTATORS_LOCKED
    end
    redis.call('ZADD', KEYS[3], timestamp, userId)
    if redis.call('ZCARD', KEYS[3]) == 1 then
        redis.call('EXPIRE', KEYS[3], 86400)
    end
    setUserPresenceToRoom(KEYS[2], roomId, 'false', PlayerRole.SPECTATOR)
    return OK

else
    return Errors.INVALID_ROLE
end
