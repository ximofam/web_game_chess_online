-- KEYS[1] = room:{roomId}
-- KEYS[2] = rooms:lobby
-- KEYS[3] = user:{hostId}:presence
-- ARGV[1] = hostId
-- ARGV[2] = settings json
-- ARGV[3] = createdAt (epoch ms)
-- ARGV[4] = roomId
-- ARGV[5] = name
-- ARGV[6] = isWhite ('true'|'false')

local err = checkUserStatusOnline(KEYS[3])
if err then
    return err
end

local isWhite = ARGV[6] == 'true'
local whiteId = isWhite and ARGV[1] or ''
local blackId = isWhite and '' or ARGV[1]
local hostRole = isWhite and PlayerRole.WHITE or PlayerRole.BLACK

redis.call('HSET', KEYS[1],
        'status', RoomStatus.WAITING,
        'hostId', ARGV[1],
        'whiteId', whiteId,
        'blackId', blackId,
        'whiteReady', 'false',
        'blackReady', 'false',
        'settings', ARGV[2],
        'createdAt', ARGV[3],
        'roomId', ARGV[4],
        'name', ARGV[5]
)

redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), ARGV[4])
setUserPresenceToRoom(KEYS[3], ARGV[4], 'true', hostRole)

redis.call('EXPIRE', KEYS[1], 86400)

return OK
