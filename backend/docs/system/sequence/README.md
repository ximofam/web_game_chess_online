# Tài liệu Sơ đồ Tuần tự Hệ thống (Sequence Diagrams Specification)
## Dự án: Web Game Chess Online & Community Platform

Tài liệu này đặc tả chi tiết luồng xử lý tuần tự (Sequence Flows) từ Client, Controllers, Services, Redis Lua Scripts, Redisson Distributed Locks, PostgreSQL Repositories, Spring AI và RabbitMQ Event Consumers trên toàn bộ các phân hệ dưới dạng **Mermaid Sequence Diagram** hiển thị trực tiếp trong Markdown.

---

## 📑 Danh mục Phân hệ & Sơ đồ Tuần tự

- [1. Phân hệ Xác thực & Quản lý Phiên (Authentication & Sessions)](#1-phân-hệ-xác-thực--quản-lý-phiên-authentication--session-management)
  - [1.1. Đăng ký tài khoản thường (User Registration)](#11-đăng-ký-tài-khoản-thường-user-registration)
  - [1.2. Đăng ký & Cấp định danh Khách (Guest Registration)](#12-đăng-ký--cấp-định-danh-khách-guest-registration)
  - [1.3. Đăng nhập tài khoản thường & Khởi tạo Redis Session (User Login)](#13-đăng-nhập-tài-khoản-thường--khởi-tạo-redis-session-user-login)
  - [1.4. Xoay vòng Refresh Token & Chống tấn công Replay (Token Rotation)](#14-xoay-vòng-refresh-token--chống-tấn-công-replay-token-rotation)
  - [1.5. Đăng xuất tài khoản & Hủy phiên Redis (User Logout)](#15-đăng-xuất-tài-khoản--hủy-phiên-redis-user-logout)
- [2. Phân hệ Người dùng & Trạng thái Hiện diện (Users & Presence)](#2-phân-hệ-người-dùng--trạng-thái-hiện-diện-users--presence)
  - [2.1. Cập nhật hồ sơ cá nhân (Update User Profile)](#21-cập-nhật-hồ-sơ-cá-nhân-update-user-profile)
  - [2.2. Tải lên ảnh đại diện & Xóa ảnh cũ (Upload Avatar)](#22-tải-lên-ảnh-đại-diện--xóa-ảnh-cũ-upload-avatar)
  - [2.3. Vòng đời Trạng thái Hiện diện qua WebSocket (Presence Lifecycle)](#23-vòng-đời-trạng-thái-hiện-diện-qua-websocket-presence-lifecycle)
- [3. Phân hệ Quản lý Phòng chơi & Sảnh chờ (Chess Room & Lobby)](#3-phân-hệ-quản-lý-phòng-chơi--sảnh-chờ-chess-room--lobby)
  - [3.1. Tạo phòng chơi mới & Đăng ký Sảnh chờ (Create Room)](#31-tạo-phòng-chơi-mới--đăng-ký-sảnh-chờ-create-room)
  - [3.2. Tham gia phòng & Đổi vị trí ghế ngồi (Join Room & Switch Seat)](#32-tham-gia-phòng--đổi-vị-trí-ghế-ngồi-join-room--switch-seat)
  - [3.3. Rời phòng & Bàn giao Chủ phòng / Xóa phòng (Leave Room Lifecycle)](#33-rời-phòng--bàn-giao-chủ-phòng--xóa-phòng-leave-room-lifecycle)
- [4. Phân hệ Trận đấu Cờ vua Thời gian thực (Chess Gameplay & Clock Engine)](#4-phân-hệ-trận-đấu-cờ-vua-thời-gian-thực-chess-gameplay--clock-engine)
  - [4.1. Kỳ thủ Sẵn sàng & Đếm ngược 3s (Ready & Countdown)](#41-kỳ-thủ-sẵn-sàng--đếm-ngược-3s-ready--countdown)
  - [4.2. Thực hiện nước đi cờ & Cập nhật Đồng hồ (Make Move & Clock Update)](#42-thực-hiện-nước-đi-cờ--cập-nhật-đồng-hồ-make-move--clock-update)
  - [4.3. Xử lý Hết giờ Nước đi do Server Kích hoạt (Turn Timeout Handling)](#43-xử-lý-hết-giờ-nước-đi-do-server-kích-hoạt-turn-timeout-handling)
  - [4.4. Đề nghị Hòa, Chấp nhận & Từ chối hòa (Draw Negotiation)](#44-đề-nghị-hòa-chấp-nhận--từ-chối-hòa-draw-negotiation)
- [5. Phân hệ Diễn đàn & Kiểm duyệt Nội dung (Forums & AI Moderation)](#5-phân-hệ-diễn-đàn--kiểm-duyệt-nội-dung-forums--ai-moderation)
  - [5.1. Tạo bài viết & Kiểm duyệt Tự động qua Spring AI (Create Post & AI Moderation)](#51-tạo-bài-viết--kiểm-duyệt-tự-động-qua-spring-ai-create-post--ai-moderation)
  - [5.2. Vòng đời Ảnh bài viết & Dọn dẹp ảnh mồ côi (Post Images Lifecycle & Orphan Cleanup)](#52-vòng-đời-ảnh-bài-viết--dọn-dẹp-ảnh-mồ-côi-post-images-lifecycle--orphan-cleanup)
  - [5.3. Bình luận Cấp 1 & Phản hồi Cấp 2 (Nested Comments & Replies)](#53-bình-luận-cấp-1--phản-hồi-cấp-2-nested-comments--replies)
  - [5.4. Tương tác Thích / Bỏ thích Bài viết & Bình luận (Like Interactions)](#54-tương-tác-thích--bỏ-thích-bài-viết--bình-luận-like-interactions)
- [6. Phân hệ Thông báo & Đẩy Realtime (Notifications & Push Delivery)](#6-phân-hệ-thông-báo--đẩy-realtime-notifications--push-delivery)
  - [6.1. Tiếp nhận & Đẩy thông báo qua WebSocket (Realtime Push Notification)](#61-tiếp-nhận--đẩy-thông-báo-qua-websocket-realtime-push-notification)

---

## 1. Phân hệ Xác thực & Quản lý Phiên (Authentication & Session Management)

### 1.1. Đăng ký tài khoản thường (User Registration)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiAuthController
    participant Service as AuthService
    participant Repo as UserRepository
    participant Hasher as PasswordEncoder
    participant DB as PostgreSQL

    Client->>Controller: POST /api/auth/register (username, email, password)
    activate Controller
    Controller->>Service: register(request)
    activate Service
    Service->>Repo: existsByUsernameOrEmail(username, email)
    Repo->>DB: SELECT COUNT(*) FROM users...
    DB-->>Repo: 0 (Chưa tồn tại)
    Service->>Hasher: encode(password)
    Hasher-->>Service: passwordHash (BCrypt)
    Service->>Repo: save(newUser)
    Repo->>DB: INSERT INTO users ...
    DB-->>Repo: User Entity (UUIDv7)
    Service-->>Controller: UserResponse
    deactivate Service
    Controller-->>Client: 201 Created (UserResponse)
    deactivate Controller
```

---

### 1.2. Đăng ký & Cấp định danh Khách (Guest Registration)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiAuthController
    participant Service as AuthService
    participant Token as TokenService
    participant Repo as UserRepository
    participant DB as PostgreSQL

    Client->>Controller: POST /api/auth/register/guest (Cookie: guestToken?)
    activate Controller
    alt Đã có guestToken hợp lệ
        Controller-->>Client: 200 OK (Giữ nguyên guestToken)
    else Chưa có hoặc token hết hạn
        Controller->>Service: registerGuest()
        activate Service
        Service->>Repo: save(guestUser)
        Repo->>DB: INSERT INTO users (role='GUEST')...
        DB-->>Repo: Guest User
        Service->>Token: generateGuestToken(guestId)
        Token-->>Service: guestToken (JWT 30 days)
        Service-->>Controller: GuestTokenResponse
        deactivate Service
        Controller-->>Client: 201 Created + Set-Cookie: guestToken=...
    end
    deactivate Controller
```

---

### 1.3. Đăng nhập tài khoản thường & Khởi tạo Redis Session (User Login)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiAuthController
    participant Service as AuthService
    participant Security as AuthenticationManager
    participant Token as TokenService
    participant Redis as Redis Store

    Client->>Controller: POST /api/auth/login (usernameOrEmail, password)
    activate Controller
    Controller->>Service: login(request)
    activate Service
    Service->>Security: authenticate(credentials)
    Security-->>Service: Authentication (Success)
    Service->>Token: generateTokens(userId, role)
    activate Token
    Token->>Token: Sinh Access Token (JWT Bearer, 15m)
    Token->>Token: Sinh jti & Refresh Token (JWT, 7d)
    Token->>Redis: SET refresh_token:<jti> session (TTL = 7d)
    Token-->>Service: TokenResponse (accessToken, refreshToken)
    deactivate Token
    Service-->>Controller: TokenResponse
    deactivate Service
    Controller-->>Client: 200 OK + Set-Cookie (refreshToken, HttpOnly, Strict)
    deactivate Controller
```

---

### 1.4. Xoay vòng Refresh Token & Chống tấn công Replay (Token Rotation)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiAuthController
    participant Token as TokenService
    participant Redis as Redis Store

    Client->>Controller: POST /api/auth/refresh (Cookie: refreshToken)
    activate Controller
    Controller->>Token: refresh(refreshToken)
    activate Token
    Token->>Token: Validate signature & Extract jti
    Token->>Redis: GET refresh_token:<jti>
    alt Không tìm thấy phiên (Hết hạn hoặc Token đã bị tái sử dụng)
        Redis-->>Token: null
        Token-->>Controller: ném UnauthorizedException (401)
        Controller-->>Client: 401 Unauthorized
    else Phiên hợp lệ
        Redis-->>Token: RefreshSession
        Token->>Redis: DEL refresh_token:<jti> (Thu hồi token cũ)
        Token->>Token: Sinh newJti, newAccessToken, newRefreshToken
        Token->>Redis: SET refresh_token:<newJti> newSession (TTL = 7d)
        Token-->>Controller: TokenResponse (newAccessToken, newRefreshToken)
        deactivate Token
        Controller-->>Client: 200 OK + Set-Cookie: refreshToken=newRefreshToken
    end
    deactivate Controller
```

---

### 1.5. Đăng xuất tài khoản & Hủy phiên Redis (User Logout)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiAuthController
    participant Token as TokenService
    participant Redis as Redis Store

    Client->>Controller: POST /api/auth/logout (Cookie: refreshToken)
    activate Controller
    Controller->>Token: logout(refreshToken)
    activate Token
    Token->>Token: Extract jti
    Token->>Redis: DEL refresh_token:<jti>
    deactivate Token
    Controller-->>Client: 200 OK + Clear Cookie (refreshToken)
    deactivate Controller
```

---

## 2. Phân hệ Người dùng & Trạng thái Hiện diện (Users & Presence)

### 2.1. Cập nhật hồ sơ cá nhân (Update User Profile)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiUserController
    participant Service as UserService
    participant Repo as UserRepository
    participant DB as PostgreSQL

    Client->>Controller: PATCH /api/users/me (fullName, gender, dateOfBirth)
    activate Controller
    Controller->>Service: updateProfile(userId, request)
    activate Service
    Service->>Repo: findById(userId)
    Repo->>DB: SELECT * FROM users WHERE id = :userId
    DB-->>Repo: User Entity
    Service->>Service: Cập nhật UserProfile embeddable
    Service->>Repo: save(user)
    Repo->>DB: UPDATE users SET full_name=..., gender=..., date_of_birth=...
    DB-->>Repo: Updated User
    Service-->>Controller: UserDetailResponse
    deactivate Service
    Controller-->>Client: 200 OK (UserDetailResponse)
    deactivate Controller
```

---

### 2.2. Tải lên ảnh đại diện & Xóa ảnh cũ (Upload Avatar)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as ApiUserController
    participant Service as UserService
    participant Cloud as CloudinaryService
    participant Repo as UserRepository
    participant DB as PostgreSQL

    Client->>Controller: PATCH /api/users/me/avatar (MultipartFile file)
    activate Controller
    Controller->>Service: updateAvatar(userId, file)
    activate Service
    Service->>Cloud: upload(file, folder="users/avatars")
    Cloud-->>Service: CloudinaryUploadResult (newUrl, newPublicId)
    Service->>Repo: findById(userId)
    Repo->>DB: SELECT * FROM users WHERE id = :userId
    DB-->>Repo: User (chứa oldAvatarPublicId)
    Service->>Repo: update avatarUrl & avatarPublicId
    Repo->>DB: UPDATE users SET avatar_url=..., avatar_public_id=...
    alt User có avatar cũ trước đó
        Service->>Cloud: deleteAsync(oldAvatarPublicId)
    end
    Service-->>Controller: ApiResponse (newUrl)
    deactivate Service
    Controller-->>Client: 200 OK {"avatarUrl": "..."}
    deactivate Controller
```

---

### 2.3. Vòng đời Trạng thái Hiện diện qua WebSocket (Presence Lifecycle)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant WS as WebSocket Config / Handlers
    participant Presence as PresenceService
    participant Redis as Redis Store (RAM)
    participant Broker as STOMP Broker (/topic/user.{id})

    Client->>WS: STOMP CONNECT (Header: Authorization)
    WS->>Presence: onConnected(userId, sessionId)
    Presence->>Redis: SADD sys:online_users userId
    Presence->>Redis: SADD user:userId:sessions sessionId
    Presence->>Redis: HSET user:userId:presence status ONLINE
    Presence->>Broker: Broadcast ONLINE status

    loop Định kỳ 15s
        Client->>WS: /app/presence.heartbeat
        WS->>Presence: renewHeartbeat(userId, sessionId)
        Presence->>Redis: EXPIRE sessions & presence
    end

    Client->>WS: STOMP DISCONNECT (hoặc rớt mạng)
    WS->>Presence: onDisconnected(userId, sessionId)
    Presence->>Redis: SREM user:userId:sessions sessionId
    alt Hết sessions (user đóng mọi tab)
        Presence->>Redis: SREM sys:online_users userId
        Presence->>Redis: HDEL user:userId:presence
        Presence->>Broker: Broadcast OFFLINE status
    end
```

---

## 3. Phân hệ Quản lý Phòng chơi & Sảnh chờ (Chess Room & Lobby)

### 3.1. Tạo phòng chơi mới & Đăng ký Sảnh chờ (Create Room)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Chủ phòng
    participant Controller as RoomController
    participant Service as RoomService
    participant Redis as Redis Store (Lua Engine)
    participant Broker as STOMP Broker (/topic/lobbies)

    Host->>Controller: POST /api/rooms (name, settings, isWhite)
    activate Controller
    Controller->>Service: createRoom(userId, request)
    activate Service
    Service->>Redis: EVAL create_room.lua (roomId, name, hostId, white/black)
    Redis->>Redis: HSET room:<id> metadata
    Redis->>Redis: ZADD room:idx:lobby createdAt roomId
    Redis->>Redis: HSET user:<hostId>:presence status IN_ROOM roomId
    Redis-->>Service: OK
    Service->>Broker: Broadcast ROOM_CREATED (RoomResponse)
    Service-->>Controller: RoomDetailResponse
    deactivate Service
    Controller-->>Host: 201 Created (RoomDetailResponse)
    deactivate Controller
```

---

### 3.2. Tham gia phòng & Đổi vị trí ghế ngồi (Join Room & Switch Seat)

```mermaid
sequenceDiagram
    autonumber
    actor Player as Người chơi
    participant Controller as RoomController
    participant Service as RoomService
    participant Redis as Redis Store (Lua Engine)
    participant BrokerRoom as STOMP Broker (/topic/room.{id})
    participant BrokerLobby as STOMP Broker (/topic/lobbies)

    Player->>Controller: POST /api/rooms/{id}/join (role=black)
    activate Controller
    Controller->>Service: joinRoom(roomId, userId, role)
    activate Service
    Service->>Redis: EVAL join_room.lua (roomId, userId, role)
    alt Ghế đã bị chiếm hoặc phòng không ở trạng thái WAITING
        Redis-->>Service: Error Code (-2, -4, ...)
        Service-->>Controller: 400 Bad Request
    else Thành công
        Redis->>Redis: HSET room:<id> blackId userId
        Redis->>Redis: HSET user:<userId>:presence status IN_ROOM
        Redis-->>Service: OK
        Service->>BrokerRoom: Broadcast PLAYER_JOINED (role=black, user)
        Service->>BrokerLobby: Broadcast ROOM_UPDATED (status WAITING)
        Service-->>Controller: RoomDetailResponse
    end
    deactivate Service
    Controller-->>Player: 200 OK (RoomDetailResponse)
    deactivate Controller
```

---

### 3.3. Rời phòng & Bàn giao Chủ phòng / Xóa phòng (Leave Room Lifecycle)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Chủ phòng
    participant Controller as RoomController
    participant Service as RoomService
    participant Redis as Redis Store (Lua Engine)
    participant BrokerRoom as STOMP Broker (/topic/room.{id})
    participant BrokerLobby as STOMP Broker (/topic/lobbies)

    Host->>Controller: POST /api/rooms/{id}/leave
    activate Controller
    Controller->>Service: leaveRoom(roomId, hostId)
    activate Service
    Service->>Redis: EVAL leave_room.lua (roomId, hostId)
    alt Còn người chơi khác trong phòng
        Redis->>Redis: Bàn giao quyền Host cho White/Black/Spectator
        Redis-->>Service: Result: HOST_TRANSFERRED (newHostId)
        Service->>BrokerRoom: Broadcast HOST_TRANSFERRED (newHost)
        Service->>BrokerLobby: Broadcast ROOM_UPDATED
    else Không còn ai trong phòng
        Redis->>Redis: EVAL delete_room.lua (DEL room:<id>, ZREM lobby)
        Redis-->>Service: Result: ROOM_EMPTY
        Service->>BrokerRoom: Broadcast ROOM_DELETED
        Service->>BrokerLobby: Broadcast ROOM_DELETED
    end
    Service-->>Controller: 200 OK
    deactivate Service
    Controller-->>Host: 200 OK
    deactivate Controller
```

---

## 4. Phân hệ Trận đấu Cờ vua Thời gian thực (Chess Gameplay & Clock Engine)

### 4.1. Kỳ thủ Sẵn sàng & Đếm ngược 3s (Ready & Countdown)

```mermaid
sequenceDiagram
    autonumber
    actor White as White Player
    actor Black as Black Player
    participant Controller as GameController
    participant Service as GameService
    participant Redis as Redis Store (Lua Engine)
    participant Scheduler as TaskScheduler
    participant Broker as STOMP Broker (/topic/room.{id})

    White->>Controller: POST /api/games/{id}/ready (isReady=true)
    Controller->>Service: setPlayerReady(roomId, whiteId, true)
    Service->>Redis: EVAL player_ready.lua (roomId, whiteId, true)
    Redis->>Redis: HSET room:<id> whiteReady=true
    Service->>Broker: Broadcast PLAYER_READY (white, true)

    Black->>Controller: POST /api/games/{id}/ready (isReady=true)
    Controller->>Service: setPlayerReady(roomId, blackId, true)
    Service->>Redis: EVAL player_ready.lua (roomId, blackId, true)
    Redis->>Redis: HSET room:<id> blackReady=true, status=COUNTDOWN
    Service->>Broker: Broadcast COUNTDOWN_STARTED (delay=3s)
    Service->>Scheduler: Lên lịch startGame sau 3 giây

    Note over Scheduler, Redis: Hết 3 giây đếm ngược
    Scheduler->>Service: execute startGame(roomId)
    Service->>Redis: EVAL start_game.lua (khởi tạo room:<id>:game, FEN, Turn)
    Service->>Broker: Broadcast GAME_STARTED
    Service->>Scheduler: Khởi động Turn Timer cho White
```

---

### 4.2. Thực hiện nước đi cờ & Cập nhật Đồng hồ (Make Move & Clock Update)

```mermaid
sequenceDiagram
    autonumber
    actor W as White Player
    participant Controller as GameController (STOMP)
    participant Service as GameService
    participant Redisson as Redisson Distributed Lock
    participant Redis as Redis Store (RAM)
    participant Engine as Chesslib Board Engine
    participant Timer as TaskScheduler (Turn Timer)
    participant Broker as STOMP Broker (/topic/room.{id})

    W->>Controller: STOMP: /app/room.{id}.move ("e2e4")
    activate Controller
    Controller->>Service: makeMove(roomId, whiteId, "e2e4")
    activate Service
    Service->>Redisson: acquireLock("lock:game:" + roomId)
    Service->>Redis: HGETALL room:<id>:game (FEN, Clocks, Turn)
    Service->>Engine: board.loadFromFen(fen)
    Service->>Engine: board.doMove(new Move("e2e4", WHITE))
    alt Nước đi Không hợp lệ
        Service-->>Controller: ném BadRequestException ("Illegal move")
    else Nước đi Hợp lệ
        Service->>Service: Trừ giờ White, cộng incrementSeconds
        Service->>Redis: HSET room:<id>:game fen=newFen, turn=black, clocks
        Service->>Redis: RPUSH room:<id>:game:moves "e2e4"
        Service->>Broker: Broadcast MOVE_MADE (e2e4, newFen, remainingClocks)
        Service->>Timer: Hủy Timer White, Lên lịch Timer mới cho Black
        alt Checkmate hoặc Hòa cờ theo luật
            Service->>Service: Trigger endGame(result, reason)
        end
    end
    Service->>Redisson: releaseLock()
    deactivate Service
    deactivate Controller
```

---

### 4.3. Xử lý Hết giờ Nước đi do Server Kích hoạt (Turn Timeout Handling)

```mermaid
sequenceDiagram
    autonumber
    participant Timer as TaskScheduler (Turn Timer)
    participant Service as GameService
    participant Redis as Redis Store (Lua Engine)
    participant Engine as Chesslib (PGN Generator)
    participant Repo as GameRepository
    participant DB as PostgreSQL
    participant Broker as STOMP Broker (/topic/room.{id})

    Timer->>Service: onTurnTimeout(roomId, turnColor=BLACK)
    activate Service
    Service->>Service: Winner = WHITE, Reason = TIMEOUT
    Service->>Redis: EVAL end_game.lua (xóa game hash, lấy moves list)
    Redis-->>Service: movesList ["e2e4", "e7e5", ...]
    Service->>Engine: convertUciToPgn(movesList)
    Engine-->>Service: pgnString (Chuẩn SAN quốc tế)
    Service->>Repo: save(new Game(white, black, pgn, result, TIMEOUT))
    Repo->>DB: INSERT INTO games ...
    Service->>Broker: Broadcast GAME_OVER (WHITE_WIN, TIMEOUT)
    deactivate Service
```

---

### 4.4. Đề nghị Hòa, Chấp nhận & Từ chối hòa (Draw Negotiation)

```mermaid
sequenceDiagram
    autonumber
    actor W as White Player
    actor B as Black Player
    participant Controller as GameController (STOMP)
    participant Service as GameService
    participant Redis as Redis Store
    participant Broker as STOMP Broker (/topic/room.{id})

    W->>Controller: /app/room.{id}.draw.offer
    Controller->>Service: offerDraw(roomId, whiteId)
    Service->>Redis: SET room:<id>:game:draw_offer whiteId NX EX 30
    alt Đã có offer đang chờ (Spam guard)
        Redis-->>Service: null (Bỏ qua)
    else Thành công
        Redis-->>Service: OK
        Service->>Broker: Broadcast DRAW_OFFERED (offeredBy=white)
    end

    alt Black Chấp nhận hòa
        B->>Controller: /app/room.{id}.draw.accept
        Controller->>Service: acceptDraw(roomId, blackId)
        Service->>Redis: GETDEL room:<id>:game:draw_offer
        Redis-->>Service: whiteId (Hợp lệ)
        Service->>Service: Trigger endGame(DRAW, DRAW_AGREEMENT)
        Service->>Broker: Broadcast GAME_OVER (DRAW, DRAW_AGREEMENT)
    else Black Từ chối hòa
        B->>Controller: /app/room.{id}.draw.decline
        Controller->>Service: declineDraw(roomId, blackId)
        Service->>Redis: GETDEL room:<id>:game:draw_offer
        Service->>Broker: Broadcast DRAW_DECLINED
    end
```

---

## 5. Phân hệ Diễn đàn & Kiểm duyệt Nội dung (Forums & AI Moderation)

### 5.1. Tạo bài viết & Kiểm duyệt Tự động qua Spring AI (Create Post & AI Moderation)

```mermaid
sequenceDiagram
    autonumber
    actor Author as Tác giả
    participant Controller as ApiPostController
    participant Service as PostService
    participant DB as PostgreSQL
    participant Rabbit as RabbitMQ (post.queue)
    participant AIWorker as PostEventListener (Spring AI)
    participant LLM as Groq / OpenAI LLM API
    participant NotifQueue as RabbitMQ (notification.queue)
    participant WS as WebSocket (/user/queue/notifications)

    Author->>Controller: POST /api/posts (title, content Tiptap JSON)
    activate Controller
    Controller->>Service: createPost(userId, request)
    activate Service
    Service->>DB: INSERT INTO posts (status = 'PENDING')
    Service->>DB: UPDATE post_images SET status='ATTACHED' WHERE id IN (...)
    Service-->>Controller: 201 Created (PostDetailResponse)
    Controller-->>Author: 201 Created
    deactivate Controller

    Note over Service, Rabbit: Giao dịch DB commit (AFTER_COMMIT)
    Service->>Rabbit: Publish PostModerationEvent (post.queue)
    deactivate Service

    Rabbit->>AIWorker: Consume PostModerationEvent
    activate AIWorker
    AIWorker->>LLM: Gửi Prompt kiểm duyệt (check_post.st + Content)
    LLM-->>AIWorker: ModerationResponse (status: APPROVED/DENIED, reason)
    AIWorker->>DB: UPDATE posts SET status=:status, approval_note=:reason
    AIWorker->>NotifQueue: Publish NotificationRequest (SYSTEM_MESSAGE)
    deactivate AIWorker

    NotifQueue->>WS: Đẩy thông báo kết quả duyệt bài tới Author
```

---

### 5.2. Vòng đời Ảnh bài viết & Dọn dẹp ảnh mồ côi (Post Images Lifecycle & Orphan Cleanup)

```mermaid
sequenceDiagram
    autonumber
    actor Author as Người soạn thảo
    participant Controller as ApiPostImageController
    participant Cloud as CloudinaryService
    participant DB as PostgreSQL (post_images)
    participant Cron as DeleteOrphanPostImageTask (1h Cron)

    Author->>Controller: POST /api/post-images (MultipartFile file)
    Controller->>Cloud: upload(file, folder="posts/images")
    Cloud-->>Controller: url, publicId
    Controller->>DB: INSERT INTO post_images (status='ORPHAN', uploaderId)
    Controller-->>Author: 201 Created {url, publicId}

    alt Trường hợp 1: Người dùng nhấn Đăng bài
        Author->>DB: Lưu Post -> Update post_images SET status='ATTACHED'
    else Trường hợp 2: Người dùng xóa ảnh hoặc hủy đăng bài
        Note over Cron, DB: Cron task chạy mỗi 1 giờ
        Cron->>DB: SELECT * FROM post_images WHERE status='ORPHAN' AND createdAt < now - 1h
        DB-->>Cron: Danh sách orphan publicIds
        Cron->>Cloud: Xóa hàng loạt publicIds trên Cloudinary
        Cron->>DB: DELETE FROM post_images WHERE id IN (...)
    end
```

---

### 5.3. Bình luận Cấp 1 & Phản hồi Cấp 2 (Nested Comments & Replies)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người bình luận
    participant Controller as ApiCommentController
    participant Service as CommentService
    participant Repo as CommentRepository
    participant DB as PostgreSQL
    participant Rabbit as RabbitMQ (notification.queue)

    User->>Controller: POST /api/comments (postId, content, parentId?)
    activate Controller
    Controller->>Service: createComment(userId, request)
    activate Service
    Service->>Repo: save(new Comment(post, author, parent, content))
    Repo->>DB: INSERT INTO comments ...
    DB-->>Repo: Comment Entity
    Service->>Rabbit: Publish NotificationRequest (POST_COMMENT hoặc COMMENT_REPLY)
    Service-->>Controller: CommentResponse
    deactivate Service
    Controller-->>User: 201 Created (CommentResponse)
    deactivate Controller
```

---

### 5.4. Tương tác Thích / Bỏ thích Bài viết & Bình luận (Like Interactions)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Controller as ApiPostController
    participant Service as PostService
    participant Repo as PostLikeRepository
    participant DB as PostgreSQL
    participant Rabbit as RabbitMQ (notification.queue)

    User->>Controller: POST /api/posts/{id}/likes?isLike=true
    activate Controller
    Controller->>Service: likePost(postId, userId, isLike)
    activate Service
    Service->>Repo: findByUserIdAndPostId(userId, postId)
    alt Chưa từng like
        Service->>Repo: save(new PostLike(user, post, isActive=true))
        Repo->>DB: INSERT INTO post_likes ...
        Service->>Rabbit: Publish NotificationRequest (POST_LIKE)
    else Đã có bản ghi (toggle)
        Service->>Repo: update isActive = :isLike
        Repo->>DB: UPDATE post_likes SET is_active = :isLike
        alt isLike == true
            Service->>Rabbit: Publish NotificationRequest (POST_LIKE)
        end
    end
    Service-->>Controller: ApiResponse ("Liked thành công")
    deactivate Service
    Controller-->>User: 200 OK
    deactivate Controller
```

---

## 6. Phân hệ Thông báo & Đẩy Realtime (Notifications & Push Delivery)

### 6.1. Tiếp nhận & Đẩy thông báo qua WebSocket (Realtime Push Notification)

```mermaid
sequenceDiagram
    autonumber
    participant Rabbit as RabbitMQ (notification.queue)
    participant Listener as NotificationEventListener
    participant Repo as NotificationRepository
    participant DB as PostgreSQL
    participant Simp as SimpMessagingTemplate (STOMP)
    actor Recipient as Người nhận (Browser)

    Rabbit->>Listener: handle(NotificationRequest)
    activate Listener
    Listener->>Repo: save(new Notification(recipient, sender, title, msg, type, metadata))
    Repo->>DB: INSERT INTO notifications ...
    DB-->>Repo: Saved Notification
    Listener->>Simp: convertAndSendToUser(recipientId, "/queue/notifications", notificationResponse)
    Simp->>Recipient: Push WebSocket frame to /user/queue/notifications
    deactivate Listener
```
