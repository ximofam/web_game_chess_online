# Workflow: Create Forum Post

## Goal
To publish a new discussion topic in the forums, complete with automated AI moderation.

## Actor
Authenticated User (Not Guest).

## Preconditions
- User is logged in.
- Post content is valid Tiptap JSON format.

## User Flow
1. User navigates to `/forum/create`.
2. User types title and uses the rich-text editor to write content.
3. (Optional) User uploads an image via the editor toolbar. Frontend uploads to backend, receives an ID, and embeds it into the editor.
4. User clicks "Submit".
5. Frontend sends HTTP POST.
6. Backend validates JSON. Extracts image IDs and links them (`ATTACHED`).
7. Backend saves post as `PENDING`.
8. Backend triggers async AI moderation via RabbitMQ.
9. Frontend receives success response and redirects user to `/forum/my-posts`.
10. User sees the post marked as "Pending Review".
11. AI worker approves the post. System sends a real-time notification to the user.

## Backend Business Rules
- Guests cannot create posts.
- Content must strictly follow Tiptap JSON schema.
- Embedded `ORPHAN` images are converted to `ATTACHED`.
- Post starts as `PENDING` and is invisible to everyone except the author.

## Frontend Behavior
- Uses a Tiptap editor component.
- Handles image uploads sequentially before final submission.
- Routes user away upon success.

## State Changes
- **PostStatus**: `PENDING` -> `APPROVED` (Async).
- **ImageStatus**: `ORPHAN` -> `ATTACHED`.

## API
- `POST /api/posts`

## WebSocket
- User receives notification via `/user/queue/notifications` upon moderation completion.

## Success
Post is created, approved by AI, and becomes visible to the public.

## Failure
- AI denies the post. It remains visible only to the author as `DENIED`.

## Edge Cases
- AI moderation RabbitMQ worker is down: The post remains `PENDING` indefinitely until the worker restarts and consumes the queue.

## Source
- Backend: `docs/business/backend/domains/forums.md`
- Frontend: `docs/business/frontend/screens/forum.md`
