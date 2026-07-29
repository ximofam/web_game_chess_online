-- KEYS[1] = LOBBY_INDEX
-- ARGV[1] = query (lowercase)
-- ARGV[2] = start
-- ARGV[3] = end (inclusive)

local query = ARGV[1]
local start_idx = tonumber(ARGV[2])
local end_idx = tonumber(ARGV[3])

-- Get all room IDs from lobby, newest first
local roomIds = redis.call('ZREVRANGE', KEYS[1], 0, -1)
local result = {}
local matched = 0

for _, id in ipairs(roomIds) do
    local name = redis.call('HGET', 'room:' .. id, 'name')
    if name then
        local lower_name = string.lower(name)
        if query == "" or string.find(lower_name, query, 1, true) then
            if matched >= start_idx and matched <= end_idx then
                table.insert(result, id)
            end
            matched = matched + 1
        end
    end
end

return {matched, result}
