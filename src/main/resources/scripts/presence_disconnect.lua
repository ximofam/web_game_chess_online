-- KEYS[1] = sessions set key      (presence:sessions:{userId})
-- KEYS[2] = presence hash key     (presence:user:{userId})
-- KEYS[3] = online users set key  (presence:online_users)
-- ARGV[1] = sessionId
-- ARGV[2] = userId
--
-- Idempotent: gọi lại nhiều lần với cùng sessionId không gây lỗi, chỉ no-op.
-- Trả về 1 nếu user vừa chuyển từ online -> offline (xoá key presence khỏi Redis), 0 nếu vẫn
-- còn session khác đang sống hoặc session không tồn tại.

local removed = redis.call('SREM', KEYS[1], ARGV[1])

if removed > 0 and redis.call('SCARD', KEYS[1]) == 0 then
  redis.call('DEL', KEYS[2])
  redis.call('SREM', KEYS[3], ARGV[2])
  return 1
end

return 0