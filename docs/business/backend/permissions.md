# Permissions
# Permission Matrix

| Operation | GUEST | USER | ADMIN |
| --- | --- | --- | --- |
| Register User | Yes | - | - |
| Register Guest | Yes | - | - |
| Login | - | Yes | Yes |
| Login Guest | Yes | No | No |
| Connect WS (Presence) | Yes | Yes | Yes |
| Update Profile | No (Implicit) | Yes | Yes |
| View Notifications | No | Yes | Yes |
| Mark Notifications Read | No | Yes | Yes |

*Note: Guest access to profile/notifications may vary by controller layer security config, but fundamentally they are restricted to guest capabilities.*
# Chess Permission Matrix

| Action                  | Host | Player | Spectator | Anonymous |
|-------------------------|------|--------|-----------|-----------|
| Create Room             | Y    | N/A    | N/A       | N         |
| Join as Spectator       | N/A  | N/A    | Y*        | N         |
| Join as Player          | N/A  | Y*     | Y*        | N         |
| Switch to Spectator     | Y    | Y      | N         | N         |
| Start Countdown (Ready) | N/A  | Y      | N         | N         |
| Make a Move             | N/A  | Y**    | N         | N         |
| Resign                  | N/A  | Y      | N         | N         |
| Offer Draw              | N/A  | Y      | N         | N         |
| Send Chat               | Y*** | Y***   | Y***      | N         |
| View Game State         | Y    | Y      | Y         | N         |

*Notes:*
- `*`: If the room has available seats, `isPrivate` is false (or invited), and `spectatorLocked` is false for spectators.
- `**`: Only if it is strictly the player's turn.
- `***`: Only if `isChatLocked` room setting is false.
# Forums Permission Matrix

| Operation | Guest | Authenticated User | Post Author |
| :--- | :---: | :---: | :---: |
| View APPROVED Posts | ✅ | ✅ | ✅ |
| View PENDING/DENIED Posts | ❌ | ❌ | ✅ |
| Create Post | ❌ | ✅ | N/A |
| Delete Post | ❌ | ❌ | ✅ |
| Like Post/Comment | ❌ | ✅ | ✅ |
| View Comments | ✅ | ✅ | ✅ |
| Create Comment / Reply | ❌ | ✅ | ✅ |
| Upload Image | ❌ | ✅ | N/A |
