# Room Screen

- **Route:** `/room/:roomId`
- **Access:** Users and Guests
- **Description:** The core real-time screen for playing games or participating in active sessions.
- **Interactions:** Heavily relies on WebSocket (STOMP) connections to sync game state, chat messages, and player presence.
- **States:** Loading connection, connected, disconnected, game states, error notifications.
