-- scripts/presence_set_status.lua
-- KEYS[1] = presence:user:{userId}
-- ARGV[1] = status value cần set (vd "ONLINE")
-- ARGV[2..N] = tên các field cần xoá khỏi hash (vd roomId, is_host, role)

redis.call('HSET', KEYS[1], 'status', ARGV[1])

if #ARGV > 1 then
    local fieldsToRemove = {}
    for i = 2, #ARGV do
        table.insert(fieldsToRemove, ARGV[i])
    end
    redis.call('HDEL', KEYS[1], unpack(fieldsToRemove))
end

return 1