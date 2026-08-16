# Domain: Chess

## Purpose
The Chess domain manages the real-time matchmaking, lobby system, and gameplay of online chess matches. It handles room creation, seating arrangements, timed games, real-time board state synchronization, chatting, and endgame resolutions (checkmate, draw, resign, timeout).

## Actors
- **Host**: The user who creates the room, configures settings, and has the implicit authority (though technically transferred if they leave).
- **Player (White/Black)**: A user participating actively in the game.
- **Spectator**: A user watching the game without participating.
- **System**: Background tasks handling turn timers, countdown timers, and game over evaluations.

## Entities
### Database Entities
- **Game**: Represents a completed chess match.
  - **Business Semantics**: Games are not persisted while in progress. They exist purely in Redis during gameplay. Only upon conclusion (endGame) are they serialized and persisted to the PostgreSQL `games` table, recording the final PGN (Portable Game Notation), players, time controls, outcome (`GameResult`, `ResultReason`), and timestamps.

### Cache/Redis Entities
The domain heavily relies on Redis for real-time state management and concurrency control via Lua scripts.
- **roomInfo:{roomId} (Hash)**: Stores room status (`WAITING`, `COUNTDOWN`, `IN_PROGRESS`), `hostId`, `whiteId`, `blackId`, `whiteReady`, `blackReady`, `settings` (JSON), and `createdAt`.
- **room:{roomId}:spectators (Sorted Set)**: Stores spectator `userId`s, scored by join timestamp.
- **room:{roomId}:game (Hash)**: Stores active game state (`turn`, `fen`, `whiteRemainingMillis`, `blackRemainingMillis`, `turnStartedAt`, `incrementMillis`). TTL is 24h, removed on game end.
- **room:{roomId}:game:moves (List)**: Stores UCI move history. TTL is 24h, removed on game end.
- **room:{roomId}:chat (List)**: Chat history (max 10 messages).
- **LOBBY_INDEX (Sorted Set)**: Global index of active rooms for matchmaking/lobby listing.
- **game:{roomId}:drawOffer (String)**: Tracks pending draw offers (`userId` of offerer). TTL is 30s.

## Realtime / WebSocket Events
- Topics: `/topic/lobbies` (Global Lobby), `/topic/room.{roomId}` (Room specific).
- Event Types:
  - `ROOM_CREATED`, `ROOM_UPDATED`, `ROOM_DELETED`
  - `PLAYER_JOINED`, `PLAYER_LEFT`, `SEAT_SWITCHED`, `HOST_TRANSFERRED`
  - `CHAT_MESSAGE`
  - `PLAYER_READY`, `GAME_COUNTDOWN` (3s delay), `COUNTDOWN_CANCELLED`, `GAME_STARTED`
  - `GAME_MOVED` (broadcasts move, next turn, fen, remaining times)
  - `GAME_OVER`
  - `DRAW_OFFERED`, `DRAW_DECLINED`

## States and Transitions
*(See `chess_states.md` for detailed state machines)*
- **RoomStatus**: `WAITING` -> `COUNTDOWN` -> `IN_PROGRESS` -> `WAITING` (if game ends)
- **GameStatus**: `FINISHED` (Database only)

## Business Operations

### 1. Create Room
- **Actor**: Host
- **Purpose**: Create a new game room.
- **Trigger**: HTTP POST `/api/rooms`
- **Preconditions**: User must be authenticated and online.
- **Main Flow**: 
  1. API receives request.
  2. RoomService calls Redis `createRoom` Lua script.
  3. Room added to `LOBBY_INDEX`.
  4. Room Hash initialized in Redis.
  5. WebSocket broadcasts `ROOM_CREATED`.
- **Business Rules**: Host is assigned the chosen color (White or Black).
- **Success Result**: Room is created, user becomes Host and Player.
- **Failure Cases**: Lua script failure.

### 2. Join Room / Switch Seat
- **Actor**: Player/Spectator
- **Purpose**: Enter an existing room or switch roles (White/Black/Spectator).
- **Trigger**: HTTP POST `/api/rooms/{roomId}/join` or `/switch-seat`
- **Preconditions**: Room exists. User is online. Room is in `WAITING` state (spectators can join anytime).
- **Main Flow**:
  1. Lua script validates availability of the target seat.
  2. Updates `roomInfo` or `spectators` ZSet.
  3. WebSocket broadcasts `PLAYER_JOINED` or `SEAT_SWITCHED`.
- **Business Rules**: Cannot join if `isPrivate` and user is not invited (though currently enforced via UI). Cannot take a seat if already taken. Spectators locked if `spectatorLocked` setting is true.
- **Failure Cases**: Seat Taken, Room Not Found, Already Seated.

### 3. Make Move
- **Actor**: Player
- **Purpose**: Execute a chess move.
- **Trigger**: WS `/room.{roomId}.move`
- **Preconditions**: Room is `IN_PROGRESS`. It is the player's turn. Game exists in Redis.
- **Main Flow**:
  1. GameService acquires Redisson lock on game.
  2. Validates move via `chesslib`.
  3. Deducts elapsed time from player's clock. Adds increment.
  4. Updates FEN, turn, and clock in Redis. Pushes to `gameMoves`.
  5. Broadcasts `GAME_MOVED`.
  6. Checks if game over (Checkmate, Stalemate, Draw). If so, triggers `endGame`.
  7. Reschedules Turn Timer for opponent.
- **Business Rules**: Move must be strictly legal (handled by chesslib). Turn timer strictly enforces timeout.
- **Failure Cases**: Not Your Turn, Illegal Move, Time Out.

### 4. End Game
- **Actor**: System / Player (via Resign/Draw)
- **Purpose**: Conclude the match and persist to DB.
- **Trigger**: Checkmate/Stalemate (from Make Move), Timeout (System Timer), Resign, Draw Agreement.
- **Preconditions**: Game is `IN_PROGRESS`.
- **Main Flow**:
  1. Turn timer cancelled.
  2. `endGame` Lua script executes: clears game Redis keys, sets room to `WAITING`.
  3. Game serialized into PostreSQL (PGN calculated, time controls recorded).
  4. Presence status of players reverted to `IN_ROOM`.
  5. Broadcasts `GAME_OVER`.
- **Business Rules**: PGN is constructed from UCI moves. Time remaining is discarded.

### 5. Leave Room
- **Actor**: Player/Spectator
- **Purpose**: Disconnect or manually leave.
- **Trigger**: HTTP POST `/api/rooms/{roomId}/leave` or Presence Disconnect.
- **Main Flow**:
  1. `leave_room` Lua script removes user.
  2. If room empty -> `ROOM_EMPTY` -> Room deleted.
  3. If host left -> `HOST_TRANSFERRED` -> Random remaining user becomes Host.
  4. If player left during `COUNTDOWN` -> Countdown cancelled.
  5. If player left during `IN_PROGRESS` -> Match ends / handled accordingly.
