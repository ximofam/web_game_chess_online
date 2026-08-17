# Frequently Asked Questions

## General
**Q: How do I create an account if I just want to try the game?**
A: You can click "Play as Guest" on the Landing screen. The system automatically creates a temporary guest session for you without requiring an email or password.

**Q: Can a guest user become a normal user?**
A: The system allows for role upgrades, but if your guest account is upgraded to a normal user, you can no longer log in using the temporary guest tokens.

**Q: What happens when my session expires?**
A: The frontend uses a Single-Flight Guard to automatically refresh your session in the background. If your refresh token also expires, you will receive a toast notification and be redirected to the login screen.

## Real-Time & Gameplay
**Q: What happens when a player disconnects?**
A: The backend detects the dropped WebSocket connection. If you are in a game room (`IN_ROOM`), a grace period applies before you are fully removed. The frontend will show a toast indicating connection loss and will attempt to reconnect.

**Q: Who can join a private room?**
A: Only users who are explicitly invited or authorized can join a private room. The frontend hides private rooms from the lobby, and the backend Lua scripts enforce the restriction.

**Q: What happens when the host leaves a room?**
A: The room does not close! The "Host" role is automatically transferred to a random remaining player or spectator in the room. If everyone leaves, the room is deleted.

**Q: How does the game start?**
A: Once both White and Black players are seated, they must toggle their "Ready" status. When both are ready, a 3-second countdown begins. If neither player cancels or disconnects, the game starts.

**Q: Why did my move fail with a "Time Out" even though my clock showed 0.1s?**
A: Time is strictly calculated on the backend based on when the request arrives. If network latency delays your move so that it reaches the server after your time has expired, the server will declare a timeout.

## Forums
**Q: Why can't I see the post I just created?**
A: All new posts are placed in a `PENDING` state and must be reviewed by our AI moderation system. You can view your own pending posts, but they won't appear publicly until they are `APPROVED`.

**Q: Why can't I reply to a specific comment?**
A: Our forums only support a maximum depth of 2 levels (A root comment and a reply to it). You cannot reply to a reply.

**Q: What happens if I upload an image but never publish the post?**
A: The image is marked as an "Orphan" and will be permanently deleted from our servers automatically after 1 hour.
