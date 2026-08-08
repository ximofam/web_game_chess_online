-- KEYS[1] = room:{roomId}
-- ARGV[1] = userId

local roomKey = KEYS[1]
local userId = ARGV[1]

local whiteId = redis.call('HGET', roomKey, 'whiteId')
if whiteId == userId then return {OK, 'white'} end

local blackId = redis.call('HGET', roomKey, 'blackId')
if blackId == userId then return {OK, 'black'} end

return {Errors.NOT_A_PLAYER}
