# Domain: Chess

## Purpose
Manages real-time matchmaking, the lobby system, and the gameplay of online chess matches. It handles room creation, seating, timed games, board state synchronization, chatting, and endgame resolutions.

## Actors
- **Host**: Room creator, can configure settings.
- **Player**: User sitting in the White or Black seat actively playing.
- **Spectator**: User observing the game.
- **System**: Enforces turn timeouts and evaluates board positions.

## User-facing capabilities
- Create a game room.
- Join a room as a player or spectator.
- Chat with other people in the room.
- Play chess (make moves, offer draws, resign).

## Business rules
- **Online Requirement**: A user must have an active WebSocket connection to create or join a room.
- **Ephemeral Games**: Active games live entirely in Redis. They are only saved to the PostgreSQL database when finished.
- **Host Transfer**: If the Host leaves, the role transfers to a random remaining user. If empty, the room deletes.
- **Strict Turn Timing**: Time is deducted based on server receipt. If remaining time falls below 0, it is a Timeout.

## User interaction
- **Dashboard**: Users see active lobbies and can join them. Private rooms are hidden.
- **Room UI**: Connects to the room's WebSocket topic. Shows connection loading states. Evaluates moves client-side for smooth UX but awaits server confirmation.
- **Disconnects**: If a player loses internet, they see a toast. If it happens during the `COUNTDOWN`, the countdown aborts.

## State
- **RoomStatus**: `WAITING`, `COUNTDOWN`, `IN_PROGRESS`.
- **GameStatus**: `FINISHED` (Only exists post-game in DB).
- **DrawOffer**: `OFFERED`, `ACCEPTED`, `DECLINED`, `EXPIRED`.

## State transitions
- **Room**: `WAITING` -> `COUNTDOWN` (Both ready) -> `IN_PROGRESS` (3s elapses) -> `WAITING` (Game ends).
- **Draw**: `OFFERED` -> `EXPIRED` (After 30s).

## System workflows
- Create Room
- Join Room
- Make Move
- End Game
- Leave Room

## API interactions
- Room creation and joining are initiated via HTTP REST (e.g., POST `/api/rooms`).
- Gameplay moves and chats happen over WebSocket.

## Realtime behavior
- Subscribes to `/topic/lobbies` (Global) and `/topic/room.{roomId}`.
- Broadcasts `PLAYER_JOINED`, `SEAT_SWITCHED`, `GAME_MOVED`, `GAME_OVER`, etc.

## Errors
- `ROOM_NOT_WAITING`, `SEAT_TAKEN`, `NOT_YOUR_TURN`, `ILLEGAL_MOVE`, `TIME_OUT`.

## Edge cases
- Server crashing during a game loses the scheduled timeout tasks.
- Network latency can cause a player to think they made a move in time, but the server rejects it as a timeout.

## Source references
Backend:
- `docs/business/backend/domains/chess.md`
Frontend:
- `docs/business/frontend/screens/room.md`
