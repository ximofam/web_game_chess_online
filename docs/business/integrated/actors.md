# System Actors

## 1. Guest (Unauthenticated User)
- **Definition**: A temporary user acting via a fallback authentication flow. Assigned a `guest_{random8}` username and a short-lived `guestToken`.
- **Capabilities**: Can view public forums, view games, play games (in public rooms or with bots), connect via WebSocket.
- **Limitations**: Cannot access Profile, Notifications, Forum Post Creation, or My Posts. Subject to expiration.

## 2. User (Registered User)
- **Definition**: A fully registered member who authenticated via email/username and password. Role = `USER`.
- **Capabilities**: Can create rooms, play games, post in forums, comment on posts, like posts/comments, update profile, and receive notifications.

## 3. Admin
- **Definition**: A user with elevated privileges. Role = `ADMIN`.
- **Capabilities**: Can manage system-wide settings, view all metrics, and moderate forum posts.

## 4. Host (Room Owner)
- **Definition**: The user who created a game room.
- **Capabilities**: Can start the game countdown, kick players, change room settings, and send chat messages. If they leave, the role transfers to a random remaining user in the room.

## 5. Player (White/Black)
- **Definition**: A user (or guest) occupying an active playing seat in a room.
- **Capabilities**: Can toggle ready state, make moves (if it's their turn), offer draws, resign, and send chat messages.

## 6. Spectator
- **Definition**: A user in a room not occupying a playing seat.
- **Capabilities**: Can observe game state, view chat, and send chat messages (unless `spectatorLocked` or `isChatLocked` is true).

## 7. System (Backend Automated Processes)
- **Definition**: Background tasks, listeners, and AI agents.
- **Capabilities**: Evaluates turn timeouts, cleans up orphan images, moderates posts via AI, handles disconnection grace periods, and generates system notifications.
