# Domain Documentation: Auth, Users, and Notifications

## Purpose
Manages user authentication, profile data, real-time presence (online status), and system/user notifications.

## Actors
- **User**: Registered member, can log in, update profile, receive notifications.
- **Guest**: Temporary user with restricted privileges and limited lifespan.
- **System**: Background tasks and event listeners that trigger notifications or clean up expired sessions/guests.

## Entities
### User (`users` table)
- **Attributes**: `username`, `email`, `passwordHash`, `role`, `isActive`, `isLocked`, `lastSeen`, `deletedAt`
- **Embedded**: `UserProfile` (avatar info, etc.)

### RefreshSession (Redis)
- **Attributes**: `userId`, `userRole`
- **Key**: `refresh_token:{jti}`
- **Lifespan**: `refreshTokenExpDays`

### Notification (`notifications` table)
- **Attributes**: `recipient`, `sender`, `title`, `message`, `type`, `metadata`, `isRead`

## States & State Transitions
- **Presence Status**: OFFLINE <-> ONLINE <-> IN_ROOM
- **Notification Status**: UNREAD -> READ (cannot be marked unread)
- **User Lifecycle**: GUEST -> USER (implicitly through upgrade, though the code throws if guest is upgraded and tries to use guest login).

## Business Operations

### 1. Register User
- **Actor**: Any unauthenticated client.
- **Purpose**: Create a new user account.
- **Trigger**: `ApiAuthController.registerUser`
- **Preconditions**: Username and Email must not exist.
- **Main Flow**: Create user with role `USER`, hash password.
- **Business Rules**: Unique constraints on username and email.
- **Side Effects**: Saves to database.
- **Success Result**: Returns user data.
- **Failure Cases**: `ConflictException` if username/email exists.

### 2. Register Guest
- **Actor**: Any unauthenticated client.
- **Purpose**: Create a temporary guest account.
- **Trigger**: `ApiAuthController.registerGuest`
- **Main Flow**: Generate random username `guest_{random8}`, create with role `GUEST`, generate token. Max attempts: 5.
- **Success Result**: Returns Guest JWT token.
- **Failure Cases**: `InternalException` if unable to generate unique guest username.

### 3. Login
- **Actor**: User.
- **Preconditions**: Valid credentials.
- **Main Flow**: Authenticate, generate access (JWT) and refresh tokens (Redis).
- **Success Result**: Returns TokenResponse.
- **Failure Cases**: `UnauthorizedException` on invalid credentials.

### 4. Connect Presence
- **Actor**: User/Guest via WebSocket.
- **Trigger**: `PresenceService.handleConnect`
- **Preconditions**: Valid auth token for WS connection.
- **Main Flow**: Update Redis sets using Lua script. If transitions to ONLINE, broadcast online count.
- **Side Effects**: Emits `UserPresenceChangedEvent`.

### 5. Disconnect Presence
- **Actor**: System (WS close or timeout).
- **Trigger**: `PresenceService.handleDisconnect`
- **Main Flow**: Remove session. If last session, update `lastSeen` in DB, emit `UserWentOfflineEvent`, update online count.
- **Side Effects**: Updates DB `lastSeen`.

## WebSocket / Realtime Behavior
- Online count is broadcasted to `/topic/presence.online-count`.
- Presence states are managed in Redis with TTLs and Lua scripts for atomicity. Race conditions handled by Redisson locks.

## Database Business Semantics
- `User` implements soft deletion (`deleted_at`).
- `Notification` stores unstructured JSON metadata.
