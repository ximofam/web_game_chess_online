# User Journeys

## The Guest Journey
1. User lands on `/landing`.
2. Chooses to try out the app without signing up.
3. Clicks "Play as Guest".
4. The system attempts guest login/registration.
5. User is routed to `/dashboard` and can enter rooms `/room/:roomId` or play with a bot.
6. The session relies on guest tokens/cookies. If cookies are cleared, guest access is lost.

## The Registered User Journey
1. User creates an account on `/register`.
2. User logs in via `/login`.
3. System fetches the user's profile and identifies their role as `USER`.
4. User accesses `/dashboard`.
5. User can view and modify their profile `/profile`.
6. User can participate in forum discussions, create posts, and view notifications.
7. User can play in real-time rooms.
