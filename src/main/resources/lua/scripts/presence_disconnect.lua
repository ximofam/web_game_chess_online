-- KEYS[1] = user:{userId}:sessions
-- KEYS[2] = user:{userId}:presence
-- KEYS[3] = sys:online_users
-- ARGV[1] = sessionId
-- ARGV[2] = userId

local removed = redis.call('SREM', KEYS[1], ARGV[1])

if removed > 0 and redis.call('SCARD', KEYS[1]) == 0 then
    -- Lấy toàn bộ thông tin presence hiện tại để trả về cho Java xử lý
    local userData = redis.call('HGETALL', KEYS[2])
    
    -- Xoá khỏi danh sách online nhưng KHÔNG xoá presence hash ở đây. Java sẽ quyết định có xoá hay không.
    redis.call('SREM', KEYS[3], ARGV[2])

    return { 1, userData }
end

return { 0, {} }