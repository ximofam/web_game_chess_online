# Workflow: Create Room

## Goal
To initialize a new real-time chess game session that other users can join.

## Actor
Authenticated User or Guest (Host).

## Preconditions
- The user must be logged in (as User or Guest).
- The user must have an active WebSocket connection (Presence status `ONLINE`).

## User Flow
1. User navigates to the Dashboard.
2. User clicks "Create Room" and optionally configures settings (time control, private/public).
3. Frontend sends an HTTP POST request to create the room.
4. Backend validates the request and user's online status.
5. Backend creates the room state in Redis using a Lua script.
6. Backend broadcasts the new room to the global lobby topic.
7. Frontend receives a successful HTTP response with the `roomId`.
8. Frontend navigates the user to `/room/:roomId`.
9. Frontend establishes real-time room communication via STOMP.

## Backend Business Rules
- A user can only create a room if they are `ONLINE` in the presence system.
- The creator is automatically assigned as the Host and given a seat (White or Black).

## Frontend Behavior
- Displays a loading state on the button during the API call.
- Handles success by immediately routing.
- If disconnected from WebSocket, disables the "Create Room" button.

## State Changes
- **RoomStatus**: Set to `WAITING`.
- **User Role in Room**: Becomes `Host` and `Player`.

## API
- `POST /api/rooms`

## WebSocket
- Broadcasts `ROOM_CREATED` to `/topic/lobbies`.

## Success
Room is created, user is routed to the room page, and the room appears in the public lobby (if not private).

## Failure
If user is offline or Redis fails, an error is returned. UI displays a toast.

## Edge Cases
- If the user creates a room but immediately closes the tab, the backend presence disconnect logic will eventually trigger `leave_room`, transferring the host or deleting the empty room.

## Source
- Backend: `docs/business/backend/domains/chess.md`
- Frontend: `docs/business/frontend/screens/dashboard.md`, `room.md`
