# Landing Screen

- **Route:** `/` or `/landing` (when unauthenticated)
- **Access:** Unauthenticated Users
- **Description:** The entry point for the application. Showcases features and provides links to Login, Register, or Play as Guest.
- **Interactions:** "Play as Guest" triggers `authService.loginGuest()` flow.
- **States:** Minimal dynamic state, primarily marketing and call-to-actions.
