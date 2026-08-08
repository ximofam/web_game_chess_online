-- KEYS[1] = room:{roomId}
-- KEYS[2] = game:{roomId}
-- ARGV[1] = winner   ("white" | "black" | "draw")
-- ARGV[2] = reason   (e.g. "timeout", "checkmate", "stalemate", "resign")

local status = redis.call('HGET', KEYS[1], 'status')
if not status then return {Errors.ROOM_NOT_FOUND} end
if status ~= RoomStatus.IN_PROGRESS then return {FAIL} end

redis.call('HSET', KEYS[1], 'status', RoomStatus.FINISHED)
redis.call('DEL', KEYS[2])

return {OK}
