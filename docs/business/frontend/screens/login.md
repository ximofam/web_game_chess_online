# Login Screen

- **Route:** `/login`
- **Access:** Unauthenticated Users
- **Description:** Allows returning users to enter their credentials (username/email and password) to obtain an access token and establish a session.
- **Interactions:** Triggers `authService.login()`.
- **States:** Displays loading spinners during API calls and toast errors upon incorrect credentials.
