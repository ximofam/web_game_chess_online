-- KEYS[1] = room:{roomId}
-- KEYS[2] = rooms:lobby
-- KEYS[3] = user:{hostId}:rooms
-- ARGV[1] = hostId
-- ARGV[2] = settings json
-- ARGV[3] = createdAt (epoch ms)
-- ARGV[4] = roomId
-- ARGV[5] = name

redis.call('HSET', KEYS[1],
  'host', ARGV[1],
  'status', 'WAITING',
  'white', ARGV[1],
  'black', '',
  'settings', ARGV[2],
  'createdAt', ARGV[3],
  'name', ARGV[5]
)

redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), ARGV[4])
redis.call('SADD', KEYS[3], ARGV[4])

return 1
