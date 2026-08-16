# Domain: Authentication, Users, and Notifications

## Purpose
Manages user identity, secure access (login/registration), temporary guest access, real-time presence (online status), and system/user notifications.

## Actors
- **User**: Registered member, can log in, update profile, receive notifications.
- **Guest**: Temporary user with restricted privileges and limited lifespan.
- **System**: Background tasks that handle disconnection grace periods and push automated notifications.

## User-facing capabilities
- Register a new account.
- Play as a Guest without signing up.
- Log in and maintain a secure session across tabs.
- View and edit profile information.
- Receive and manage in-app notifications.

## Business rules
- **Unique Identity**: Usernames and emails must be unique.
- **Guest Lockout**: Guests upgraded to a User role cannot log back in with their guest tokens.
- **Token Lifecycles**: Short-lived stateless JWT access tokens, paired with stateful Redis-backed refresh tokens (JTI).

## User interaction
- **Landing / Login**: The frontend provides a fallback authentication flow where "Play as Guest" creates a temporary account automatically. Users use standard forms to log in or register.
- **Navigation**: Guests are restricted from protected routes (Profile, Notifications) via the `ProtectedLayout` component.
- **Connection Loss**: If the WebSocket drops, the `SocketProvider` shows a toast notification, but the backend maintains a "grace period" so the user isn't immediately booted from games.

## State
- **Presence**: `OFFLINE`, `ONLINE`, `IN_ROOM`.
- **Notification**: `UNREAD`, `READ`.
- **User Role**: `GUEST`, `USER`, `ADMIN`.

## State transitions
- **Presence**: `OFFLINE` -> `ONLINE` (when connecting to WS). `ONLINE` -> `OFFLINE` (when all WS sessions close).
- **Notifications**: `UNREAD` -> `READ`. (Cannot go back to `UNREAD`).

## System workflows
- Login
- Register User
- Register Guest
- Disconnect / Reconnect

## API interactions
- `ApiAuthController.registerUser`, `registerGuest`, `login`
- Profile and Notification endpoints using `Axios` and `TanStack Query`.

## Realtime behavior
- WebSocket connects upon authentication. Heartbeat is sent every 10s to `/app/presence.heartbeat`.
- Online count is broadcast to `/topic/presence.online-count`.
- Notifications are pushed dynamically.

## Errors
- `ConflictException`: Username/Email taken.
- `UnauthorizedException`: Tokens missing, expired, or invalid. (Frontend catches via `GlobalApiErrorHandler` and redirects to `/login`).

## Edge cases
- Refresh token expiration causing a sudden forced logout (Frontend Single-Flight Guard mitigates race conditions during token refresh).

## Source references
Backend:
- `docs/business/backend/domains/auth_users_notifications.md`
Frontend:
- `docs/business/frontend/authentication.md`
- `docs/business/frontend/websocket.md`
