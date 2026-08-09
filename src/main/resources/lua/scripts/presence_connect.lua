-- KEYS[1] = user:{userId}:sessions
-- KEYS[2] = user:{userId}:presence
-- KEYS[3] = sys:online_users
-- ARGV[1] = sessionId
-- ARGV[2] = userId
--
-- Trả về 1 nếu user vừa chuyển từ offline -> online, 0 nếu vẫn đang online
-- (đã có session khác từ trước).

local wasEmpty = (redis.call('SCARD', KEYS[1]) == 0)
redis.call('SADD', KEYS[1], ARGV[1])
redis.call('PERSIST', KEYS[2])

if wasEmpty then
    redis.call('HSETNX', KEYS[2], 'status', UserStatus.ONLINE)
    redis.call('SADD', KEYS[3], ARGV[2])
    return 1
end

return 0