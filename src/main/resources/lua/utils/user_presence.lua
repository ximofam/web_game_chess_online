local UserStatus = {
    OFFLINE = 'OFFLINE',
    ONLINE = 'ONLINE',
    IN_ROOM = 'IN_ROOM',
    PLAYING = 'PLAYING'
}

local function buildPresenceKey(userId)
    return 'user:' .. userId .. ':presence'
end

local function checkUserStatusOnline(presenceKey)
    local status = redis.call('HGET', presenceKey, 'status')
    if not status or status == UserStatus.OFFLINE then
        return Errors.USER_OFFLINE
    end
    if status == UserStatus.IN_ROOM or status == UserStatus.PLAYING then
        return Errors.ALREADY_IN_ROOM
    end
    return nil
end

local function setUserPresenceToRoom(presenceKey, roomId, isHost, role)
    redis.call('HSET', presenceKey, 'status', UserStatus.IN_ROOM, 'roomId', roomId, 'is_host', tostring(isHost), 'role', role)
end

local function setPlayingIfInRoom(presenceKey)
    local status = redis.call('HGET', presenceKey, 'status')
    if status == UserStatus.IN_ROOM then
        redis.call('HSET', presenceKey, 'status', UserStatus.PLAYING)
        return OK
    end
    return FAIL
end
