-- KEYS[1] = user:{userId}:rooms
-- KEYS[2] = rooms:lobby
--
-- Logic: Lặp qua tất cả roomId của user. Nếu status == WAITING thì xoá room và khỏi lobby.
-- Xoá roomId đó khỏi user:{userId}:rooms.
-- Trả về danh sách các roomId đã bị xoá.

local deleted_rooms = {}
local rooms = redis.call('SMEMBERS', KEYS[1])

for _, roomId in ipairs(rooms) do
    local roomKey = 'room:' .. roomId
    local status = redis.call('HGET', roomKey, 'status')
    
    if status == 'WAITING' then
        redis.call('DEL', roomKey)
        redis.call('ZREM', KEYS[2], roomId)
        redis.call('SREM', KEYS[1], roomId)
        table.insert(deleted_rooms, roomId)
    end
end

return deleted_rooms
