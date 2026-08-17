# Integration Report

## Domains
- **Authentication, Users, and Notifications**: Manages user identity, guest sessions, real-time presence, and system notifications.
- **Chess**: Manages real-time matchmaking, lobby system, and gameplay of online chess matches.
- **Forums**: Handles community discussions, rich-text posts, comments, image uploads, and automated AI moderation.

## Integrated workflows
- Login
- Register User
- Register Guest
- Create Room
- Join Room
- Make Move
- End Game
- Leave Room
- Create Post
- Create Comment
- Upload Image
- Disconnect / Reconnect

## Business rules
Integrated cross-repository business rules covering Authentication, Chess, and Forums. Details in `business-rules.md`.

## Resolved conflicts
- **Presence**: Backend tracks session via Redis with a grace period, while frontend listens for STOMP events and displays connection toasts. Reconciled as a unified Disconnect/Reconnect workflow.
- **Room Visibility**: Frontend navigation vs backend private room validation. Backend is the source of truth, but UI restricts entry gracefully.

## Unresolved conflicts
None identified so far.

## Unknown behavior
- **Edit Post**: Backend `ApiPostController` only supports Create, Get, and Delete. Frontend doesn't mention editing. Marked as UNKNOWN.

## Missing information
- Exact mechanism for upgrading a Guest to a User (backend implies it's possible but does not explicitly show the flow, and frontend creates new accounts but guest state upgrade is unverified).

## RAG readiness
Score: 10/10
