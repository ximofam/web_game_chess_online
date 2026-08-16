# Authentication Flow

The frontend application uses a sophisticated authentication flow encompassing both registered users and guest users, managed primarily via the `AuthContext` and `authService`.

## Roles
- **USER**: A registered and fully authenticated user.
- **GUEST**: A temporary user session for unauthenticated visitors wanting to try features.

## Main Processes

### Initialization
Upon application load, `AuthContext` triggers `initAuth()` which attempts to refresh the token.
- If successful, the user's profile is fetched and the session is established.
- If it fails (401), the user remains unauthenticated and is routed to the Landing Page.

### Guest Login Fallback
1. The app first tries to `loginGuest()`.
2. If this fails (no existing guest account/cookie), it calls `registerGuest()`.
3. After successful guest registration, it retries `loginGuest()`.
4. Guest tokens are stored, and the user acts as a GUEST role.

### Registered User Login
1. User provides username/email and password.
2. `authService.login()` is called.
3. Upon success, an access token is stored.
4. User profile is fetched, identifying them as `USER` (isGuest: false).

### Token Refresh and Single-Flight Guard
To prevent race conditions during token expiration, `refreshToken` is wrapped in a single-flight promise guard (`refreshPromiseRef`). Only one refresh request can be active at a time.

### Logout
Logout clears local storage tokens and state, and calls `authService.logout()`. The user is redirected to the Landing Page.
