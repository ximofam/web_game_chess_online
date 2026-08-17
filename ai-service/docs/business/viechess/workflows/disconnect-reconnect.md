# Workflow: Disconnect and Reconnect

## Goal
To handle scenarios where a user's internet connection drops and restores.

## Actor
Any User or Guest.

## Preconditions
- User is connected via WebSocket.

## User Flow
1. User's internet connection drops.
2. Frontend `SocketProvider` detects the STOMP disconnection.
3. Frontend displays a toast indicating connection loss.
4. Backend detects the WS close event.
5. Backend invokes `PresenceService.handleDisconnect`.
6. Backend realizes the user is `IN_ROOM` and initiates a grace period rather than instantly marking them offline or forfeiting the game.
7. User's internet restores.
8. Frontend automatically attempts to reconnect STOMP (or user clicks manual reconnect).
9. STOMP reconnects successfully. Frontend displays a success toast.
10. Frontend re-fetches or re-subscribes to room state to sync any missed events.

## Backend Business Rules
- A user isn't immediately declared `OFFLINE` if they disconnect while playing a game. A grace period prevents instant forfeit.
- If the grace period expires without reconnection, the user is formally removed from the room, resulting in a forfeit/resignation if the game is active.

## Frontend Behavior
- Provides non-intrusive toasts to inform the user of network status.
- Automatically handles STOMP reconnection.

## State Changes
- Backend Presence: `ONLINE` -> `IN_ROOM` (retained during grace) -> `ONLINE` (upon reconnect).

## API
- Presence Heartbeat: `/app/presence.heartbeat`

## WebSocket
- Disconnect and Reconnect events on the SockJS layer.

## Success
User reconnects before the grace period ends and continues playing smoothly.

## Failure
User fails to reconnect, grace period expires, user is kicked from room, game ends.

## Edge Cases
- If the user had multiple tabs open, closing one tab triggers a disconnect, but the backend sees >0 remaining sessions, so the user remains `ONLINE` without issue.

## Source
- Backend: `docs/business/backend/state-machines.md`, `domains/auth_users_notifications.md`
- Frontend: `docs/business/frontend/websocket.md`
