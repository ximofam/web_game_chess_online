local function checkUserStatusOnline(presenceKey)
    local status = redis.call('HGET', presenceKey, 'status')
    if not status or status == 'OFFLINE' then
        return Errors.USER_OFFLINE
    end
    if status == 'IN_ROOM' or status == 'PLAYING' then
        return Errors.ALREADY_IN_ROOM
    end
    return nil
end

local function setUserPresenceToRoom(presenceKey, roomId, isHost, role)
    redis.call('HSET', presenceKey, 'status', 'IN_ROOM', 'roomId', roomId, 'is_host', tostring(isHost), 'role', role)
end
