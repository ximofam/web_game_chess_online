# Domain: Forums

## Purpose
Handles community discussions. Allows users to create rich-text posts, comment on them, upload images, and like content. Enforces quality through automated AI moderation.

## Actors
- **Guest**: Can view approved posts.
- **User**: Can create posts, comments, upload images, and toggle likes.
- **System**: Moderates posts asynchronously (AI) and cleans up orphaned images (Cron).

## User-facing capabilities
- Browse public forum posts.
- Create a rich-text post with images.
- View personal posts (including pending/denied ones).
- Reply to posts and to other comments.
- Like posts and comments.

## Business rules
- **Tiptap JSON**: Posts must be submitted in the strict Tiptap JSON schema.
- **Image Lifecycle**: Images uploaded are `ORPHAN` until attached to a post via JSON parsing. `ORPHAN` images older than 1 hour are deleted.
- **Async AI Moderation**: All new posts start as `PENDING` and must be reviewed by the AI ChatClient before becoming `APPROVED`.
- **Comment Depth**: Maximum nesting depth is 2 (Root -> Reply). You cannot reply to a reply.

## User interaction
- **Forum UI**: Displays loading states and empty lists. 
- **Editor**: Uses a Tiptap-based rich-text editor. Images are uploaded to the backend first, which returns an ID/URL that is then embedded into the editor.
- **Replies**: The UI hides the "Reply" button on 2nd-level comments to enforce the depth limit gracefully.

## State
- **PostStatus**: `PENDING`, `APPROVED`, `DENIED`.
- **ImageStatus**: `ORPHAN`, `ATTACHED`.

## State transitions
- **Post**: `PENDING` -> `APPROVED` or `DENIED` (via AI worker).
- **Image**: `ORPHAN` -> `ATTACHED` (when post is created).

## System workflows
- Create Post
- Upload Image
- Create Comment
- Async AI Moderation

## API interactions
- REST CRUD operations for posts and comments.

## Realtime behavior
- AI moderation is processed asynchronously via RabbitMQ.
- When moderation completes, a system notification is pushed to the author via WebSocket (`RoutingKeys.NOTIF_PUSH`).

## Errors
- `BadRequestException`: Invalid Tiptap JSON, or trying to reply > 2 levels deep.
- `NotFoundException`: Attempting to view a post that is PENDING/DENIED (unless author).

## Edge cases
- A user uploads an image but never publishes the post. The image breaks after 1 hour when the cron deletes it.

## Source references
Backend:
- `docs/business/backend/domains/forums.md`
Frontend:
- `docs/business/frontend/screens/forum.md`
