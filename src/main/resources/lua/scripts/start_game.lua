-- KEYS[1] = room:{roomId}
-- KEYS[2] = game:{roomId}
-- ARGV[1] = roomId
-- ARGV[2] = currentTimeMs
-- ARGV[3] = initialFen

local roomId = ARGV[1]
local currentTimeMs = tonumber(ARGV[2])
local initialFen = ARGV[3]

local status = redis.call('HGET', KEYS[1], 'status')
if not status then return {Errors.ROOM_NOT_FOUND} end
if status ~= RoomStatus.COUNTDOWN then return {Errors.ROOM_NOT_COUNTDOWN} end

local startAt = tonumber(redis.call('HGET', KEYS[1], 'startAt'))
if not startAt or currentTimeMs < startAt then
    return {Errors.START_TIME_NOT_REACHED}
end

local whiteId = redis.call('HGET', KEYS[1], 'whiteId')
local blackId = redis.call('HGET', KEYS[1], 'blackId')

local settingsRaw = redis.call('HGET', KEYS[1], 'settings')
local settings = {}
if settingsRaw then
    local ok, decoded = pcall(cjson.decode, settingsRaw)
    if ok then settings = decoded end
end

local timeMinutes = settings.timeMinutes or 10
local incrementSeconds = settings.incrementSeconds or 0

local initialTimeMillis = timeMinutes * 60 * 1000
local incrementMillis = incrementSeconds * 1000

-- Đổi trạng thái phòng thành IN_PROGRESS (đang chơi)
redis.call('HSET', KEYS[1], 'status', RoomStatus.IN_PROGRESS)

-- Tạo thông tin ván đấu
redis.call('HMSET', KEYS[2],
    'roomId', roomId,
    'whiteId', whiteId,
    'blackId', blackId,
    'whiteRemainingMillis', initialTimeMillis,
    'blackRemainingMillis', initialTimeMillis,
    'incrementMillis', incrementMillis,
    'turn', 'white',
    'turnStartedAt', startAt,
    'startAt', startAt,
    'fen', initialFen
)

return {OK, whiteId, blackId, 'white'}
