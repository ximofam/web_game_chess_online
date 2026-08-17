# System Edge Cases

## 1. Disconnect during Countdown
**Scenario:** Both players are ready, countdown starts (3s). One player loses internet connection before it finishes.
**Resolution:** The backend detects the disconnect, triggers `leave_room`, which intercepts the `COUNTDOWN` state and immediately aborts the countdown, reverting the room to `WAITING`.
**User Consequence:** The remaining player sees the countdown cancel and the opponent vanish.

## 2. Server Crash during Active Game
**Scenario:** The backend crashes while a game is `IN_PROGRESS`.
**Resolution:** Games reside purely in Redis. If Redis persists, a restarted backend might recover them, but scheduled turn timers (Java ScheduledFutures) are lost in memory. Thus, games might become zombified (no timeouts trigger).
**User Consequence:** Users might be stuck in a game that never times out.

## 3. Network Lag causing Timeout
**Scenario:** A player makes a move with 0.1s left on their client clock. Network latency takes 0.2s to reach the server.
**Resolution:** The server calculates `elapsed = now - turnStartedAt`. The elapsed time is 0.3s. The server registers `newRemaining = -0.2s` and throws a `TIME_OUT` error.
**User Consequence:** The player believes they made the move in time, but the server rejects it as a timeout. The server is the source of truth.

## 4. Replying to a Reply
**Scenario:** A malicious user bypasses the frontend UI restrictions and sends an API request to set a reply as the parent of a new comment.
**Resolution:** Backend strictly enforces depth. Throws `BadRequestException` ("Chỉ được reply tối đa 2 cấp").
**User Consequence:** API call fails cleanly.

## 5. Abandoned Image Uploads
**Scenario:** A user writes a very long forum post, uploads an image, but takes more than 1 hour to submit the post.
**Resolution:** The background cron job deletes the `ORPHAN` image from Cloudinary and the database after 1 hour. When the user finally submits, the post is created, but the image URL embedded in the Tiptap JSON will return a 404 from Cloudinary.
**User Consequence:** The post contains a broken image link.

## 6. Guest Token Upgrade Collision
**Scenario:** A Guest attempts to authenticate with their `guestToken` after an administrator or backend process changes their role to `USER`.
**Resolution:** Backend throws `UnauthorizedException`.
**User Consequence:** The user is forcibly logged out and must re-authenticate (presumably through normal Login).
