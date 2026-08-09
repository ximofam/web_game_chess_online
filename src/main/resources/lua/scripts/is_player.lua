-- KEYS[1] = room:{roomId}
-- ARGV[1] = userId

local roomKey = KEYS[1]
local userId = ARGV[1]

local whiteId = redis.call('HGET', roomKey, 'whiteId')
if whiteId == userId then
    return { OK, PlayerRole.WHITE }
end

local blackId = redis.call('HGET', roomKey, 'blackId')
if blackId == userId then
    return { OK, PlayerRole.BLACK }
end

return { Errors.NOT_A_PLAYER }
