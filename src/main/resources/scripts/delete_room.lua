-- KEYS[1] = room:{roomId}
-- KEYS[2] = room:idx:lobby (LOBBY_INDEX)
-- KEYS[3] = room:{roomId}:spectators
-- KEYS[4] = room:{roomId}:chat
-- ARGV[1] = roomId
--
-- Xoá toàn bộ room (dùng khi host rời) và trả về danh sách userId từng ở trong room
-- (players trước, spectators sau với tiền tố 'spectator:') để Java reset presence.

local white = redis.call('HGET', KEYS[1], 'white') or ''
local black = redis.call('HGET', KEYS[1], 'black') or ''
local host  = redis.call('HGET', KEYS[1], 'host')  or ''

local userIds = {}
if white ~= '' then table.insert(userIds, white) end
if black ~= '' and black ~= white then table.insert(userIds, black) end
if host ~= '' and host ~= white and host ~= black then table.insert(userIds, host) end

local spectators = redis.call('ZRANGE', KEYS[3], 0, -1)
for _, sid in ipairs(spectators) do
    table.insert(userIds, 'spectator:' .. sid)
end

redis.call('DEL', KEYS[1], KEYS[3], KEYS[4])
redis.call('ZREM', KEYS[2], ARGV[1])

return userIds