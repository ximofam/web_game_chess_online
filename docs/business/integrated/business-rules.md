# Business Rules

## BR-AUTH-01 — Unique Identity
Rule: Usernames and Emails must be unique across the system.
Backend enforcement: Throws `ConflictException` during registration.
Frontend behavior: Displays a toast error upon receiving the conflict.
User-visible consequence: User is prompted to choose another username/email.
Source: Backend: `business-rules.md`, Frontend: `errors.md`

## BR-AUTH-02 — Guest Upgrades Lockout
Rule: A Guest user cannot log in via `guestToken` if their role has been upgraded to USER.
Backend enforcement: Validates role against token during login, throws `UnauthorizedException`.
Frontend behavior: Global API error handler catches 401, clears session, redirects to landing.
User-visible consequence: Session expires, forced logout.
Source: Backend: `business-rules.md`, Frontend: `authentication.md`

## BR-AUTH-03 — Short-lived JWT vs Stateful Refresh
Rule: Access tokens are short-lived stateless JWTs; Refresh tokens are stateful and stored in Redis with `{jti}`.
Backend enforcement: Generates tokens accordingly. `TokenService.deleteRefreshSession` removes JTI from Redis on logout.
Frontend behavior: Uses `AuthContext` with a Single-Flight Guard to refresh access tokens implicitly when they expire.
User-visible consequence: Seamless session unless the refresh token expires.
Source: Backend: `business-rules.md`, Frontend: `authentication.md`

## BR-PRES-01 — Concurrent Sessions
Rule: Presence is tracked per-session. A user is `ONLINE` if they have at least one active session.
Backend enforcement: Redis tracking with Lua scripts.
Frontend behavior: Heartbeat sent to `/app/presence.heartbeat` every 10s.
User-visible consequence: Multiple tabs can be open without appearing offline.
Source: Backend: `business-rules.md`, Frontend: `websocket.md`

## BR-CHESS-01 — Online Requirement
Rule: A user can only create or join a room if they are online.
Backend enforcement: Lua scripts validate presence status.
Frontend behavior: UI requires a valid WebSocket connection before enabling "Create/Join" actions.
User-visible consequence: Buttons are disabled or show errors if disconnected.
Source: Backend: `business-rules.md`, Frontend: `room.md`

## BR-CHESS-02 — Ephemeral Games
Rule: A game is not persisted to the database until it finishes. Active games reside in Redis.
Backend enforcement: Read/write to Redis during `IN_PROGRESS`, serialize to PostgreSQL on `endGame`.
Frontend behavior: Real-time board state is completely dependent on WebSocket synchronization.
User-visible consequence: If the game server entirely crashes mid-game and loses Redis, the game might vanish without a trace.
Source: Backend: `business-rules.md`, Frontend: `room.md`

## BR-CHESS-03 — Private Room Enforcement
Rule: If a room is private, unauthorized players cannot join.
Backend enforcement: Enforced at Lua join script level.
Frontend behavior: Hides private rooms from the public lobby list.
User-visible consequence: User must be explicitly invited to join a private room.
Source: Backend: `business-rules.md`, Frontend: `screens/dashboard.md`

## BR-CHESS-04 — Host Transfer
Rule: When the host leaves the room, the host role is transferred to a random remaining user. If empty, it is deleted.
Backend enforcement: Handled inside `leave_room` Lua script. Broadcasts `HOST_TRANSFERRED`.
Frontend behavior: Receives event and updates UI to show the new host's privileges (e.g., settings panel becomes available).
User-visible consequence: The room continues to exist as long as at least one person remains.
Source: Backend: `business-rules.md`, Frontend: `room.md`

## BR-CHESS-05 — Countdown Interruptions
Rule: If a player leaves or cancels ready during the `COUNTDOWN` state, the countdown is immediately cancelled.
Backend enforcement: Transitions from `COUNTDOWN` to `WAITING` and broadcasts `COUNTDOWN_CANCELLED`.
Frontend behavior: UI stops the 3-second visual countdown and reverts to the ready toggle.
User-visible consequence: Game start is aborted.
Source: Backend: `business-rules.md`, Frontend: `room.md`

## BR-CHESS-06 — Strict Turn Timing
Rule: Time deducted equals elapsed since `turnStartedAt`. If this brings time below zero, timeout.
Backend enforcement: Server calculates time upon receiving move, verifies elapsed, checks scheduled timer.
Frontend behavior: Calculates estimated time client-side for smooth UI but waits for server truth on `GAME_MOVED`.
User-visible consequence: Network lag could theoretically cause a timeout if the server evaluates the arrival time > remaining time.
Source: Backend: `faq.md`, Frontend: `room.md`

## BR-FRM-01 — Tiptap JSON Requirement
Rule: A post must be submitted with content formatted as valid Tiptap JSON schema.
Backend enforcement: Throws `BadRequestException` if content root is not 'doc' or is invalid JSON.
Frontend behavior: Tiptap rich-text editor generates this exact format natively.
User-visible consequence: Seamless unless a malicious API request is made.
Source: Backend: `business-rules.md`, Frontend: `forum.md`

## BR-FRM-02 — Image Lifecycle
Rule: Images are `ORPHAN` until attached to a post via Tiptap JSON extraction. `ORPHAN` images > 1h are deleted.
Backend enforcement: Cron job deletes old orphans. Post creation parses JSON via DFS to find `data-public-id` and sets to `ATTACHED`.
Frontend behavior: Uploads image first, receives URL/ID, then embeds into the editor.
User-visible consequence: If a user uploads an image but abandons the post, it is cleaned up automatically.
Source: Backend: `business-rules.md`, Frontend: `forum.md`

## BR-FRM-03 — Asynchronous AI Moderation
Rule: Newly created posts start in `PENDING` state and undergo automated AI moderation before becoming `APPROVED` or `DENIED`.
Backend enforcement: RabbitMQ worker processes AI moderation.
Frontend behavior: Shows post only to the author if PENDING/DENIED.
User-visible consequence: Post isn't immediately visible to the public.
Source: Backend: `business-rules.md`, Frontend: `forum.md`

## BR-FRM-04 — Comment Depth Limit
Rule: Comments support a maximum nesting depth of 2 (Root Comment -> Reply).
Backend enforcement: Throws `BadRequestException` ("Chỉ được reply tối đa 2 cấp").
Frontend behavior: UI hides the "Reply" button on 2nd-level comments.
User-visible consequence: Users cannot create infinitely deep threads.
Source: Backend: `business-rules.md`, Frontend: `forum.md`
