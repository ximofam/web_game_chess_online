# State Machines
# State Machines

## User Presence State Machine
- **OFFLINE**: Initial state.
- **ONLINE**: User has >0 active WebSocket sessions. (Trigger: `handleConnect`)
- **IN_ROOM**: User is actively in a game room. (Set externally, respected by presence).
- **Transition: ONLINE -> OFFLINE**: All sessions expire or disconnect. (Trigger: `handleDisconnect`)
- **Transition: IN_ROOM -> OFFLINE (Grace)**: Disconnect while in room leaves user in a grace period for reconnection before full offline.

## Notification State Machine
- **UNREAD**: Initial state upon creation.
- **READ**: Triggered by user viewing the notification (`markAsRead` or `markAllAsRead`). Terminal state.
# Chess State Machines

## Room Status State Machine

```mermaid
stateDiagram-v2
    [*] --> WAITING : Room Created
    WAITING --> COUNTDOWN : Both players ready
    COUNTDOWN --> WAITING : Player leaves or cancels ready
    COUNTDOWN --> IN_PROGRESS : Countdown finishes (3s)
    IN_PROGRESS --> WAITING : Game ends (Checkmate/Draw/Resign/Timeout)
    WAITING --> [*] : Room Deleted (Empty)
```

## Game State Machine

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS : Start Game (Redis)
    IN_PROGRESS --> FINISHED : Move evaluation (Checkmate, Stalemate, 50-move rule, Threefold Repetition)
    IN_PROGRESS --> FINISHED : Timer expiration (Timeout)
    IN_PROGRESS --> FINISHED : Player action (Resign, Draw Agreement)
```

## Draw Offer State Machine

```mermaid
stateDiagram-v2
    [*] --> OFFERED : Player offers draw
    OFFERED --> ACCEPTED : Opponent accepts (Game Ends)
    OFFERED --> DECLINED : Opponent declines
    OFFERED --> EXPIRED : 30 seconds pass
    DECLINED --> [*]
    EXPIRED --> [*]
```
# Forums State Machines

## Post Status State Machine
```mermaid
stateDiagram-v2
    [*] --> PENDING : Create Post
    PENDING --> APPROVED : AI Moderation (Pass)
    PENDING --> DENIED : AI Moderation (Fail)
    APPROVED --> [*] : Soft Delete
    DENIED --> [*] : Soft Delete
```

## PostImage Status State Machine
```mermaid
stateDiagram-v2
    [*] --> ORPHAN : Upload Image
    ORPHAN --> ATTACHED : Submit Post (Image linked)
    ORPHAN --> [*] : Cron (Older than 1h, Hard Delete)
```
