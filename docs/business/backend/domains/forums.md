# Forums Domain

## Purpose
The forums domain handles the community discussion aspects of the application. It allows users to create rich-text posts (with images), comment on posts, and like both posts and comments. The system includes an automated AI-driven moderation flow to ensure content quality and safety.

## Actors
- **Guest (Unauthenticated)**: Can browse and view approved posts and comments.
- **User (Authenticated)**: Can create posts, view own pending/denied posts, delete own posts, upload images, comment on approved posts (up to 2 levels deep), and toggle likes on posts and comments.
- **System (Moderation AI)**: Automatically reviews new posts asynchronously and transitions them to APPROVED or DENIED states based on content.
- **System (Cron Job)**: Periodically cleans up orphan image uploads.

## Entities
- **Post**: Represents a user-generated forum discussion. Stores Tiptap JSON content.
- **Comment**: A response to a Post or a reply to another Comment.
- **PostImage**: Represents images uploaded for posts. Linked to Cloudinary.
- **PostLike / CommentLike**: Represents a user's active/inactive like on an entity.
- **ApprovalInfo**: Embedded value object in Post, tracking moderation details (approved by, at, reason).

## States
### PostStatus
- **PENDING**: Initial state upon post creation. Awaiting AI moderation. Only the author can view it.
- **APPROVED**: Moderation passed. Visible to everyone. Open for comments and likes.
- **DENIED**: Moderation failed. Visible only to the author. Cannot be interacted with.

### ImageStatus
- **ORPHAN**: Initial state when an image is uploaded but not yet linked to a published post.
- **ATTACHED**: Linked to a created Post. Excluded from the orphan cleanup cron.

## State Transitions
1. **Post Moderation**: `PENDING` -> `APPROVED` or `DENIED` (Triggered by Async AI Moderation Event).
2. **Image Attachment**: `ORPHAN` -> `ATTACHED` (Triggered by Post Creation extracting image IDs).

## Business Operations

### Create Post
- **Actor**: User
- **Purpose**: Create a new discussion thread.
- **Trigger**: User submits title and Tiptap JSON content.
- **Preconditions**: User must be logged in. Content must be valid Tiptap JSON.
- **Main Flow**:
  1. Save Post as `PENDING`.
  2. Parse Tiptap JSON to extract Cloudinary `public_id`s of images.
  3. Update matching `PostImage`s to `ATTACHED` status.
  4. Publish `PostModerationEvent` to RabbitMQ.
- **Business Rules**: Tiptap JSON parsing; Image extraction via DFS.
- **State Changes**: `PostImage` becomes `ATTACHED`.
- **Side Effects**: Async moderation triggered via RabbitMQ.
- **Success Result**: Returns `PostDetailResponse` with initial PENDING state.
- **Failure Cases**: Invalid JSON format throws BadRequestException.

### View Post
- **Actor**: Guest / User
- **Purpose**: View post content.
- **Preconditions**: Post must be `APPROVED` (unless the author is viewing their own post via a specific API).
- **Main Flow**:
  1. Fetch post.
  2. Increment `viewCount` by 1.
  3. Check if current user has liked the post.
- **Side Effects**: `viewCount` incremented.
- **Success Result**: Returns post content, stats (likes/comments count), and user's like status.
- **Failure Cases**: Post is PENDING/DENIED or deleted throws NotFoundException.

### Create Comment
- **Actor**: User
- **Purpose**: Add a comment or reply to an existing comment.
- **Preconditions**: Post must be `APPROVED`. If replying, parent comment must exist and belong to the same post.
- **Main Flow**:
  1. Verify post is APPROVED.
  2. If `parentId` is provided, verify parent exists, belongs to the post, and is a root comment (not a reply itself).
  3. Save new Comment.
- **Business Rules**: Maximum nesting depth for comments is 2 (Root -> Reply).
- **Failure Cases**: Replying to a reply (depth > 2) throws BadRequestException. Parent comment not matching post throws BadRequestException.

### AI Moderation (Async)
- **Actor**: System (RabbitMQ Consumer)
- **Purpose**: Automatically review posts for inappropriate content.
- **Trigger**: `PostModerationEvent` received.
- **Preconditions**: Post must be in `PENDING` state.
- **Main Flow**:
  1. Send post title and content to ChatClient AI.
  2. Parse AI response (Status + Reason).
  3. Update Post status to `APPROVED` or `DENIED`.
  4. Publish `PostModerationCompletedEvent`.
- **Side Effects**: Pushes a system notification to the author via `RoutingKeys.NOTIF_PUSH`.
- **Failure Cases**: AI fails or returns invalid response (retried via RabbitMQ mechanics).

### Upload Post Image
- **Actor**: User
- **Purpose**: Upload an image before embedding it in a post.
- **Main Flow**:
  1. Upload file to Cloudinary (`posts/images` folder).
  2. Save `PostImage` entity as `ORPHAN`.
- **State Changes**: Creates `ORPHAN` image.

### Delete Orphan Images (Cron)
- **Actor**: System Cron
- **Purpose**: Clean up unused image uploads to save storage.
- **Trigger**: Every 1 hour.
- **Main Flow**: Find `ORPHAN` PostImages older than 1 hour. Delete from Cloudinary and DB.

## Database Business Semantics
- `Post`, `Comment` are `SoftDeleteModel`s (soft deleted via `deleted_at`).
- `PostLike`, `CommentLike` use unique logical constraints (per user per entity) and toggle an `isActive` flag instead of hard deletion to avoid thrashing.
- Post content uses JSON (Tiptap schema) stored in a text column.

## Realtime / Async Behavior
- Heavy reliance on RabbitMQ for decoupling:
  - `PostModerationEvent` routed to AI worker.
  - `PostModerationCompletedEvent` triggers a transactional event listener which then pushes `NotificationRequest` to RabbitMQ for realtime user notifications.
