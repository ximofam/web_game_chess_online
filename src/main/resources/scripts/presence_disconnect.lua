-- KEYS[1] = sessions set key      (presence:sessions:{userId})
-- KEYS[2] = presence hash key     (presence:user:{userId})
-- ARGV[1] = sessionId
--
-- Idempotent: gọi lại nhiều lần với cùng sessionId không gây lỗi, chỉ no-op.
-- Trả về 1 nếu user vừa chuyển từ online -> offline (xoá key presence khỏi Redis), 0 nếu vẫn
-- còn session khác đang sống hoặc session không tồn tại.

local removed = redis.call('SREM', KEYS[1], ARGV[1])

if removed > 0 and redis.call('SCARD', KEYS[1]) == 0 then
  redis.call('DEL', KEYS[2])
  return 1
end

return 0