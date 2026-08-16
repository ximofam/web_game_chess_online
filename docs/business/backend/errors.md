# Error Semantics
# Error Semantics

- **ConflictException**: Thrown during registration if the username or email already exists.
- **UnauthorizedException**: Thrown when tokens are missing, expired, invalid, or when a guest attempts to login but has been upgraded.
- **NotFoundException**: Thrown when querying a user or notification ID that does not exist or does not belong to the user.
- **InternalException**: Thrown when the system fails to generate a unique guest username after 5 attempts.
# Chess Error Semantics

- **ROOM_NOT_FOUND**: The specified room ID does not exist in Redis.
- **ROOM_NOT_WAITING**: The user attempted to join as a player, but the room is currently in COUNTDOWN or IN_PROGRESS.
- **ROOM_IS_PRIVATE**: The user attempted to join a private room without proper authorization (currently enforced at Lua join script level).
- **ALREADY_SEATED**: The user is already in the room as White, Black, Host, or Spectator, and tried to perform an invalid seat transition.
- **SEAT_TAKEN**: The requested seat (White or Black) is already occupied by another user.
- **SPECTATORS_LOCKED**: The user attempted to join as a spectator, but the room settings have `spectatorLocked` = true.
- **INVALID_ROLE**: The role specified during join or switch-seat is not White, Black, or Spectator.
- **NOT_YOUR_TURN**: The user attempted to make a move, but the game state indicates it is the opponent's turn.
- **ILLEGAL_MOVE**: The user submitted a move that is invalid according to standard chess rules (validated by `chesslib`).
- **TIME_OUT**: The user attempted to make a move, but their clock had already fallen below zero based on server time calculation.
- **START_TIME_NOT_REACHED**: A race condition where the game start was triggered before the countdown elapsed.
- **NOT_IN_ROOM**: Validation failure where a player is missing from the room presence list at game start.
# Forums Errors

- `BadRequestException`: "Nội dung không đúng định dạng Tiptap" - Post content JSON root is not type 'doc'.
- `BadRequestException`: "Nội dung không phải JSON hợp lệ" - Post content is unparseable JSON.
- `BadRequestException`: "Bạn cần đăng nhập để xem bài viết của mình" - Missing authentication context for /my endpoints.
- `BadRequestException`: "Chỉ được reply tối đa 2 cấp" - User attempts to set a reply as the parent of another reply.
- `BadRequestException`: "Comment cha không thuộc bài viết này" - Parent comment's `postId` does not match the requested `postId`.
- `BadRequestException`: "sortBy chỉ hỗ trợ createdAt hoặc likeCount" - Invalid sort parameter for comments.
- `ForbiddenException`: "Bạn không có quyền xóa bài viết này" - User attempts to delete a post they did not author.
- `NotFoundException`: "PostId {id} không tồn tại hoặc chưa được duyệt" - Post is deleted, not approved, or does not exist.
- `NotFoundException`: "CommentId {id} không tồn tại" - Comment does not exist or is deleted.
