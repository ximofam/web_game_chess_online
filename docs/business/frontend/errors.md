# Errors and Fallbacks

## Error Pages
- **403 Forbidden (`/403`)**: Displayed when a user attempts to access a route or feature they do not have permissions for (e.g., a Guest trying to access the Profile page).
- **404 Not Found (`/404`)**: The fallback for any undefined or missing routes. Users are directed here if they attempt to navigate to a non-existent URL.

## API Error Handling
- Handled at the top level via `GlobalApiErrorHandler`.
- Global toasts provide immediate, non-intrusive feedback to users regarding failed actions (e.g., failed to connect to room, failed to authenticate).

## Token Expiration
- If the access token and refresh token expire, a global hook catches the 401 error. The user's session is forcibly cleared, a toast notifies them, and they are redirected to the root or `/login`.
