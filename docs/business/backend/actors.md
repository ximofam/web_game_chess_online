# Actors

This document describes the various actors (roles) that interact with the system.

## 1. Guest
- **Definition:** An anonymous user who has not registered but is assigned a `guestToken` to interact with certain parts of the system.
- **Capabilities:** Can view public forums, view games, play games (in some configurations, though usually games require user status depending on rules), but has a limited lifespan and cannot retain persistent data like registered users.

## 2. User
- **Definition:** A fully registered and authenticated user (logged in via email/password).
- **Capabilities:** Can create rooms, play games, post in forums, comment, like posts, and manage their own profile.

## 3. Admin
- **Definition:** A user with administrative privileges (Role = `ADMIN`).
- **Capabilities:** Can manage system-wide settings, moderate forum posts, and has elevated privileges over normal users.

## 4. Host (Room Owner)
- **Definition:** A user who creates a game room. 
- **Capabilities:** Can start the game, kick players, change room settings, and acts as the authoritative client for certain room sync events.

## 5. Player
- **Definition:** A user (or guest) who is currently occupying a seat (white or black) in a chess game room.
- **Capabilities:** Can make moves, offer draws, resign, and toggle their ready state.

## 6. Spectator
- **Definition:** A user who joins a room but is not occupying a playing seat.
- **Capabilities:** Can observe the game state and chat, but cannot make moves.

## 7. System
- **Definition:** The automated backend processes (Crons, AI Agents, Scheduled Tasks).
- **Capabilities:** Cleans up orphan images, deletes expired guests, automatically moderates posts via AI, and sends system notifications.
