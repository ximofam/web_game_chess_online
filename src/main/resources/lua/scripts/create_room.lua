-- KEYS[1] = room:{roomId}
-- KEYS[2] = rooms:lobby
-- KEYS[3] = user:{hostId}:presence
-- ARGV[1] = hostId
-- ARGV[2] = settings json
-- ARGV[3] = createdAt (epoch ms)

local err = checkUserStatusOnline(KEYS[3])
if err then
    return err
end

redis.call('HSET', KEYS[1],
        'status', RoomStatus.WAITING,
        'hostId', ARGV[1],
        'whiteId', ARGV[1],
        'blackId', '',
        'whiteReady', 'false',
        'blackReady', 'false',
        'settings', ARGV[2],
        'createdAt', ARGV[3],
        'roomId', ARGV[4],
        'name', ARGV[5]
)

redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), ARGV[4])
setUserPresenceToRoom(KEYS[3], ARGV[4], 'true', PlayerRole.WHITE)

redis.call('EXPIRE', KEYS[1], 86400)

return OK
