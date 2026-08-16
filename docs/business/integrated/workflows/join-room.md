# Workflow: Join Room

## Goal
To enter an existing game room to either play or spectate.

## Actor
Authenticated User or Guest.

## Preconditions
- The user must be logged in.
- The user must be `ONLINE`.
- The target room must exist.

## User Flow
1. User clicks on an active lobby from the Dashboard or uses a direct link.
2. Frontend sends an HTTP POST request to join the room.
3. Backend validates the request via Lua script.
4. Backend updates the room's participant lists in Redis.
5. Backend broadcasts the join event to the room's topic.
6. Frontend receives a successful response.
7. Frontend navigates to `/room/:roomId` (if not already there).
8. Frontend subscribes to `/topic/room.{roomId}`.

## Backend Business Rules
- Cannot join a `private` room without an invite (currently enforced by hiding the room from UI).
- Cannot join as a player if the room is in `COUNTDOWN` or `IN_PROGRESS` (returns `ROOM_NOT_WAITING`).
- Cannot join as a spectator if `spectatorLocked` is true.

## Frontend Behavior
- Private rooms are hidden from the lobby.
- If joining fails (e.g., room full), UI shows a toast error and does not navigate.

## State Changes
- User is added to the room's Hash (as White/Black) or the spectator ZSet in Redis.

## API
- `POST /api/rooms/{roomId}/join`

## WebSocket
- Broadcasts `PLAYER_JOINED` to `/topic/room.{roomId}`.

## Success
User is in the room and sees the current participants and chat.

## Failure
- `ROOM_NOT_FOUND`
- `ROOM_NOT_WAITING`
- `SEAT_TAKEN`
- `SPECTATORS_LOCKED`

## Edge Cases
- Two users click "Join" on the last open seat simultaneously. Redis Lua scripting ensures only one succeeds atomicity; the other gets `SEAT_TAKEN`.

## Source
- Backend: `docs/business/backend/domains/chess.md`, `errors.md`
- Frontend: `docs/business/frontend/screens/room.md`
