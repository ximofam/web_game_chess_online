# WebSocket Behavior (SockJS/STOMP)

The real-time features of the application are powered by a WebSocket connection using STOMP over SockJS, managed via `SocketProvider` and a `stompClientManager`.

## Connection Lifecycle
- **Connect**: The socket automatically attempts to connect when a user becomes authenticated (`isAuthenticated` is true).
- **Disconnect**: The socket gracefully disconnects upon unmount or when the user logs out.
- **Reconnect**: Users can manually trigger a reconnection, which forces a disconnect and a retry.

## Heartbeat and Presence
- When connected, the `SocketProvider` initiates an automatic heartbeat to `/app/presence.heartbeat`.
- The interval is configured via `VITE_HEARTBEAT_INTERVAL_MS` or defaults to 10000ms (10 seconds).
- This keeps the connection alive and signals the user's online presence to the server.

## Status Notifications
The `SocketProvider` listens for status changes (CONNECTED, DISCONNECTED).
- It dispatches a toast notification to the UI alerting the user when connection is lost or re-established.
- This ensures users are aware of their real-time state, crucial for synchronous features like game rooms or chat.
