-- KEYS[1] = user:{userId}:presence
-- KEYS[2] = user:{userId}:sessions
-- ARGV[1] = status
-- ARGV[2..N] = key, value pairs

if redis.call('SCARD', KEYS[2]) == 0 then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'status', ARGV[1], unpack(ARGV, 2))

return 1