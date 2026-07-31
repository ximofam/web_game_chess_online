-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:idx:lobby (LOBBY_INDEX)
-- KEYS[3] = room:{roomId}:spectators
-- KEYS[4] = room:{roomId}:chat
-- ARGV[1] = roomId

local whiteId = redis.call('HGET', KEYS[1], 'whiteId') or ''
local blackId = redis.call('HGET', KEYS[1], 'blackId') or ''
local hostId  = redis.call('HGET', KEYS[1], 'hostId')  or ''

local userIds = {}
if whiteId ~= '' then table.insert(userIds, whiteId) end
if blackId ~= '' and blackId ~= whiteId then table.insert(userIds, blackId) end
if hostId ~= '' and hostId ~= whiteId and hostId ~= blackId then table.insert(userIds, hostId) end

local spectators = redis.call('ZRANGE', KEYS[3], 0, -1)
for _, sid in ipairs(spectators) do
    table.insert(userIds, sid)
end

redis.call('DEL', KEYS[1], KEYS[3], KEYS[4])
redis.call('ZREM', KEYS[2], ARGV[1])

return userIds