# Workflow: Make Move

## Goal
To execute a legal chess move during an active game.

## Actor
Player (White or Black).

## Preconditions
- The room status must be `IN_PROGRESS`.
- It must be the actor's turn.
- The move must be legal.

## User Flow
1. User drags and drops a piece on the frontend chessboard.
2. Frontend evaluates the move locally for immediate visual feedback.
3. Frontend sends the move (in UCI format) to the server via WebSocket.
4. Backend acquires a lock on the game in Redis.
5. Backend validates the move using `chesslib`.
6. Backend calculates the elapsed time and deducts it from the player's clock.
7. Backend updates the FEN, turn, and clock in Redis.
8. Backend broadcasts the move and new game state.
9. Both frontends receive the broadcast and update their board/clocks definitively.

## Backend Business Rules
- Strict turn enforcement (`NOT_YOUR_TURN`).
- Strict move legality enforcement (`ILLEGAL_MOVE`).
- Time deduction: `newRemaining = remaining - (now - turnStartedAt)`. If `< 0`, triggers Timeout.

## Frontend Behavior
- Speculatively moves the piece on the UI for responsiveness, but snaps it back if the server rejects it.
- Smoothly interpolates the clock locally, but resyncs strictly upon receiving the server's `GAME_MOVED` event.

## State Changes
- Turn changes to the opponent.
- Remaining time is updated (+ increment).
- FEN string is updated.
- If checkmate/stalemate is detected, transitions to `FINISHED` state.

## API
- None (WebSocket only).

## WebSocket
- Sent: `/room.{roomId}.move`
- Received: `GAME_MOVED` on `/topic/room.{roomId}`

## Success
The move is registered and the opponent's turn begins.

## Failure
- The piece snaps back on the frontend if the backend throws an error or evaluates a timeout.

## Edge Cases
- Network lag causes the move to arrive after the server's scheduled turn timer fires. The move is rejected, and a `GAME_OVER` (Timeout) is broadcast.

## Source
- Backend: `docs/business/backend/domains/chess.md`, `faq.md`
- Frontend: `docs/business/frontend/screens/room.md`
