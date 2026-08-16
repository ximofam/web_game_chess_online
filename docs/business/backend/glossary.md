# Glossary
# Glossary

- **JWT**: JSON Web Token, used for stateless access verification.
- **JTI**: JWT ID, used to identify unique refresh tokens stored in Redis.
- **Guest**: A temporary user role with an expiration period, assigned randomly generated usernames.
- **Presence**: The system of tracking whether a user is currently connected to the server via WebSocket.
- **Session**: A specific WebSocket connection instance for a user. A single user may have multiple concurrent sessions.
- **Soft Delete**: Marking a record as deleted via a timestamp (`deleted_at`) rather than physically removing it from the database.
# Chess Glossary

- **FEN (Forsyth–Edwards Notation)**: A standard notation for describing a particular board position of a chess game.
- **PGN (Portable Game Notation)**: A standard plain text format for recording chess games.
- **UCI (Universal Chess Interface)**: A standard string format representing a chess move (e.g., e2e4).
- **Time Control**: The time limits set for a game, composed of total time in minutes and increment in seconds.
- **Increment**: The amount of time added to a player's clock after they make a move.
- **Host**: The owner/creator of a game room who can modify settings or start the game.
- **Spectator**: A user inside a room but not participating as White or Black.
- **Stalemate**: A draw condition where the player whose turn it is to move has no legal moves and is not in check.
- **Checkmate**: A win condition where the player's king is in check and has no legal moves to escape.
- **Draw Offer**: A proposal by one player to end the game in a draw, which the other player can accept or decline.
# Forums Glossary

- **Tiptap**: The rich-text editor framework used by the frontend. The backend expects content in Tiptap's specific JSON node structure (e.g., `{"type": "doc", "content": [...]}`).
- **Orphan Image**: An image uploaded to the server/Cloudinary but not yet finalized as part of a published post.
- **AI Moderation**: The automated process utilizing an LLM (ChatClient) to review post content and approve or deny it based on predefined prompt rules.
- **Soft Delete**: Entities like Post and Comment are never truly deleted from the database; instead, a `deleted_at` timestamp is set.
