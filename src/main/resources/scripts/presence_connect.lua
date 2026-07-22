-- KEYS[1] = sessions set key      (presence:sessions:{userId})
-- KEYS[2] = presence hash key     (presence:user:{userId})
-- KEYS[3] = online users set key  (presence:online_users)
-- ARGV[1] = sessionId
-- ARGV[2] = userId
--
-- Trả về 1 nếu user vừa chuyển từ offline -> online, 0 nếu vẫn đang online
-- (đã có session khác từ trước).

local wasEmpty = (redis.call('SCARD', KEYS[1]) == 0)
redis.call('SADD', KEYS[1], ARGV[1])

if wasEmpty then
  redis.call('HSET', KEYS[2], 'status', 'online')
  redis.call('SADD', KEYS[3], ARGV[2])
  return 1
end

return 0