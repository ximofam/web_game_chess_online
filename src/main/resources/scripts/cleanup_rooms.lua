-- KEYS[1] = user:{userId}:rooms
-- KEYS[2] = rooms:lobby
--
-- Logic: Lặp qua tất cả roomId của user. Nếu status == WAITING thì xoá room và khỏi lobby.
-- Xoá roomId đó khỏi user:{userId}:rooms.
-- Trả về số room đã dọn.

local count = 0
local rooms = redis.call('SMEMBERS', KEYS[1])

for _, roomId in ipairs(rooms) do
    local roomKey = 'room:' .. roomId
    local status = redis.call('HGET', roomKey, 'status')
    
    if status == 'WAITING' then
        redis.call('DEL', roomKey)
        redis.call('ZREM', KEYS[2], roomId)
        redis.call('SREM', KEYS[1], roomId)
        count = count + 1
    end
end

return count
