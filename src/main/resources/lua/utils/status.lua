local OK = 1
local HOST_LEFT = 2
local WHITE_LEFT = 3
local BLACK_LEFT = 4
local SPECTATOR_LEFT = 5
local FAIL = 0
local Errors = {
    ROOM_NOT_FOUND = -1,
    ROOM_NOT_WAITING = -2,
    ALREADY_SEATED = -3,
    SEAT_TAKEN = -4,
    SPECTATORS_LOCKED = -5,
    INVALID_ROLE = -6,
    ROOM_IS_PRIVATE = -7,
    USER_OFFLINE = -8,
    ALREADY_IN_ROOM = -9,
    NOT_IN_ROOM = -10
}
