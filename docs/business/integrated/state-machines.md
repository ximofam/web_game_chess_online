# State Machines

## 1. User Presence
Describes the real-time online status of a user. Source of truth is Redis.

```mermaid
stateDiagram-v2
    [*] --> OFFLINE : User is disconnected
    OFFLINE --> ONLINE : handleConnect (>0 WS sessions)
    ONLINE --> IN_ROOM : User enters a room (External Trigger)
    IN_ROOM --> OFFLINE : handleDisconnect (Grace period applied)
    ONLINE --> OFFLINE : handleDisconnect (All sessions end)
```

## 2. Room Status
Describes the lifecycle of a real-time chess room.

```mermaid
stateDiagram-v2
    [*] --> WAITING : Room Created
    WAITING --> COUNTDOWN : Both players toggle Ready
    COUNTDOWN --> WAITING : Player leaves or cancels Ready
    COUNTDOWN --> IN_PROGRESS : Countdown finishes (3s delay)
    IN_PROGRESS --> WAITING : Game ends (Checkmate/Draw/Resign/Timeout)
    WAITING --> [*] : Room Deleted (Becomes Empty)
```

## 3. Game State
Describes the lifecycle of an active chess match in Redis.

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS : Start Game (Redis state created)
    IN_PROGRESS --> FINISHED : Move evaluation (Checkmate, Stalemate, 50-move rule, Threefold Repetition)
    IN_PROGRESS --> FINISHED : Timer expiration (Timeout triggered by System)
    IN_PROGRESS --> FINISHED : Player action (Resign, Draw Agreement)
```

## 4. Draw Offer State
Describes the lifecycle of a proposal to draw.

```mermaid
stateDiagram-v2
    [*] --> OFFERED : Player offers draw
    OFFERED --> ACCEPTED : Opponent accepts (Triggers Game End)
    OFFERED --> DECLINED : Opponent declines
    OFFERED --> EXPIRED : 30 seconds pass (TTL expires)
    DECLINED --> [*]
    EXPIRED --> [*]
```

## 5. Forum Post Status
Describes the lifecycle of a discussion post regarding moderation.

```mermaid
stateDiagram-v2
    [*] --> PENDING : Create Post
    PENDING --> APPROVED : AI Moderation (Pass)
    PENDING --> DENIED : AI Moderation (Fail)
    APPROVED --> [*] : Soft Delete
    DENIED --> [*] : Soft Delete
```

## 6. Forum Image Status
Describes the lifecycle of an uploaded image.

```mermaid
stateDiagram-v2
    [*] --> ORPHAN : Image Uploaded
    ORPHAN --> ATTACHED : Submit Post (Image linked via Tiptap JSON)
    ORPHAN --> [*] : Cron (Older than 1h, Hard Delete)
```
