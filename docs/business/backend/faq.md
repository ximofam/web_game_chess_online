# FAQ
# FAQ

**Q: Can a guest user become a normal user?**
A: Yes, though the exact upgrade mechanism may be elsewhere. If upgraded, their `role` changes and they can no longer log in via guest tokens.

**Q: How is presence tracked across multiple tabs/devices?**
A: Redis tracks individual session IDs per user. A user only goes offline when their last active session expires or disconnects.

**Q: What happens if the server crashes? Are users stuck online?**
A: Presence sessions have a TTL in Redis. If the server crashes, heartbeats stop, and the sessions will expire, naturally marking users offline.

**Q: Are refresh tokens invalidated on logout?**
A: Yes, `TokenService.deleteRefreshSession` removes the token from Redis based on its JTI.
# Chess FAQ

**Q: Where are active games stored?**
A: Active games are entirely managed in Redis using Lua scripts for atomicity. They are only written to the PostgreSQL database when the game ends.

**Q: How is time calculated?**
A: The server calculates elapsed time upon receiving a move request. `elapsed = now - turnStartedAt`. `newRemaining = remaining - elapsed`. If `newRemaining <= 0`, the player times out. Otherwise, `committedRemaining = newRemaining + incrementMillis`.

**Q: What happens if a player disconnects?**
A: They may trigger a `handle_playing_disconnect` flow which could result in a forfeit, resignation, or a pause depending on specific disconnect logic implementations.

**Q: How is countdown handled?**
A: When both players are ready, a countdown timer is scheduled 3 seconds into the future. If anyone cancels ready or leaves, the countdown is cancelled.

**Q: Can a spectator chat?**
A: Spectators can chat unless the room's chat is explicitly locked (`isChatLocked` = true).

**Q: Who evaluates checkmate/stalemate?**
A: The backend uses `chesslib` to evaluate the board state (`fen`) after every move. The client is not trusted to claim a checkmate.
# Forums FAQ

**Q: Can users edit their posts?**
A: UNKNOWN — NOT DETERMINED FROM CODE. The current `ApiPostController` only supports Create, Get, and Delete operations. There is no update/edit endpoint visible.

**Q: What happens if a user replies to a reply?**
A: The system will block it and return a `BadRequestException` ("Chỉ được reply tối đa 2 cấp").

**Q: How does the system know which images to attach to a post?**
A: When a post is created, the backend parses the Tiptap JSON, performs a Depth-First Search (DFS) for node type `image`, extracts the `data-public-id` attribute, and links those specific IDs in the database.

**Q: Are likes soft-deleted?**
A: No, likes (`PostLike`, `CommentLike`) use an `isActive` boolean flag to toggle state, preventing database insert/delete thrashing.

**Q: How long does moderation take?**
A: Moderation is asynchronous and handled by a background RabbitMQ worker. It typically depends on the LLM response time, but happens shortly after post creation.
