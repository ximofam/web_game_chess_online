-- KEYS[1] = room:{roomId}
-- KEYS[2] = game:{roomId}
-- KEYS[3] = game:{roomId}:moves
-- ARGV[1] = color
-- ARGV[2] = newFen
-- ARGV[3] = move
-- ARGV[4] = currentTimeMs

local color = ARGV[1]
local newFen = ARGV[2]
local move = ARGV[3]
local currentTimeMs = tonumber(ARGV[4])

local turn = redis.call('HGET', KEYS[2], 'turn')
if not turn then return {Errors.GAME_NOT_FOUND} end

if color ~= turn then
    return {Errors.NOT_YOUR_TURN}
end

local turnStartedAt = tonumber(redis.call('HGET', KEYS[2], 'turnStartedAt') or currentTimeMs)
local incrementMillis = tonumber(redis.call('HGET', KEYS[2], 'incrementMillis') or 0)
local timeKey = color .. 'RemainingMillis'
local remainingTime = tonumber(redis.call('HGET', KEYS[2], timeKey))

local elapsed = currentTimeMs - turnStartedAt
if elapsed < 0 then elapsed = 0 end
local newRemaining = remainingTime - elapsed

if newRemaining <= 0 then
    redis.call('HSET', KEYS[2], timeKey, 0)
    return {Errors.TIME_OUT}
end

newRemaining = newRemaining + incrementMillis

local nextTurn = (turn == 'white') and 'black' or 'white'
redis.call('HMSET', KEYS[2], 
    'turn', nextTurn, 
    'fen', newFen, 
    timeKey, newRemaining,
    'turnStartedAt', currentTimeMs
)

if move and move ~= "" then
    redis.call('RPUSH', KEYS[3], move)
end

return {OK, nextTurn, color, newRemaining}