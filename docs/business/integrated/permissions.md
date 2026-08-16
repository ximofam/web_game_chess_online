# Permission Matrix

## General System Permissions

| Operation | Guest | User | Admin |
| :--- | :---: | :---: | :---: |
| Access Dashboard / Landing | ✅ | ✅ | ✅ |
| Register Account | ✅ | ❌ | ❌ |
| Register/Login Guest | ✅ | ❌ | ❌ |
| Login User | ❌ | ✅ | ✅ |
| Connect WebSocket (Presence) | ✅ | ✅ | ✅ |
| View / Edit Profile | ❌ | ✅ | ✅ |
| View Notifications | ❌ | ✅ | ✅ |

*Note: Frontend enforces protected routes (`/profile`, `/notifications`) via `ProtectedLayout`. Backend enforces endpoints based on token validation.*

## Chess Room Permissions

| Action | Host | Player | Spectator |
| :--- | :---: | :---: | :---: |
| Create Room | ✅ | N/A | N/A |
| Join as Spectator | N/A | N/A | ✅* |
| Join as Player | N/A | ✅* | ✅* |
| Switch to Spectator | ✅ | ✅ | ❌ |
| Start Countdown (Ready) | N/A | ✅ | ❌ |
| Make a Move | N/A | ✅** | ❌ |
| Resign / Offer Draw | N/A | ✅ | ❌ |
| Send Chat | ✅*** | ✅*** | ✅*** |
| View Game State | ✅ | ✅ | ✅ |

- `*`: Must be online. Room must not be full. If private, user must be invited. If `spectatorLocked` is true, spectators cannot join.
- `**`: Strictly only if it is the player's turn.
- `***`: Only if the room setting `isChatLocked` is false.

## Forums Permissions

| Operation | Guest | User | Post Author |
| :--- | :---: | :---: | :---: |
| View APPROVED Posts | ✅ | ✅ | ✅ |
| View PENDING/DENIED Posts | ❌ | ❌ | ✅ |
| Create Post | ❌ | ✅ | N/A |
| Delete Post | ❌ | ❌ | ✅ |
| Edit Post | ❌ | ❌ | UNKNOWN |
| Like Post / Comment | ❌ | ✅ | ✅ |
| View Comments | ✅ | ✅ | ✅ |
| Create Comment (up to depth 2) | ❌ | ✅ | ✅ |
| Upload Image (ORPHAN) | ❌ | ✅ | N/A |
