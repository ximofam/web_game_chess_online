-- KEYS[1] = sessions set key      (presence:sessions:{userId})
-- KEYS[2] = presence hash key     (presence:user:{userId})
-- ARGV[1] = sessionId
-- ARGV[2] = current epoch second (timestamp)
--
-- Trả về 1 nếu user vừa chuyển từ offline -> online, 0 nếu vẫn đang online
-- (đã có session khác từ trước).

local wasEmpty = (redis.call('SCARD', KEYS[1]) == 0)
redis.call('SADD', KEYS[1], ARGV[1])

if wasEmpty then
  redis.call('HSET', KEYS[2], 'status', 'online', 'lastSeen', ARGV[2])
  return 1
end

return 0