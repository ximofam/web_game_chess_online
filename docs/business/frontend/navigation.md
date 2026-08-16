# Navigation

The frontend utilizes `react-router-dom` for navigation.

## Routes
- **Public Routes (PublicLayout)**: Accessible without an account, or as a Guest. Includes Landing, Dashboard (for Guests/Users), Login, Register, Learn, Forum, Room, 403, and 404.
- **Protected Routes (ProtectedLayout)**: Accessible only to registered and authenticated users. Includes Profile, Notifications, Forum Creation, and My Posts.

## Index Routing
The root path (`/`) dynamically renders based on authentication state:
- If `isAuthenticated` is true, renders `Dashboard`.
- Otherwise, renders `LandingPage`.

## Layouts
- **PublicLayout**: Wraps general pages accessible to anyone.
- **ProtectedLayout**: Requires a verified user session (non-guest). Unauthenticated users trying to access protected paths are typically redirected.
