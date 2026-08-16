# Permissions

Permissions dictate which parts of the application a user can access based on their authentication state and role.

## Roles Supported (Frontend)
- **UNAUTHENTICATED**: Has access to Landing, Login, Register. Cannot access Dashboard, Rooms, Profile, etc.
- **GUEST**: Temporary user role. Can access Dashboard, Play Bot, and join public Rooms. Cannot access Profile, Notifications, Forum Post Creation, or My Posts.
- **USER**: Registered user. Has full access to all features, including protected routes and full forum capabilities.

## Enforcement
- **ProtectedLayout**: Requires a full `USER` role. Automatically redirects or blocks `GUEST` or `UNAUTHENTICATED` users.
- Backend enforcement: UNKNOWN (Assumed to validate tokens strictly on protected API endpoints).
