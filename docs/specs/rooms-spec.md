# Đặc tả API: Quản lý Phòng chơi (Rooms Spec)

Tài liệu này đặc tả chi tiết về cấu trúc dữ liệu, luồng hoạt động và các API/WebSocket Topic liên quan đến hệ thống quản
lý Phòng chơi (Lobby & Rooms).

---

## 1. Tổng quan & Kiến trúc

Hệ thống sử dụng **Redis** làm database chính cho Room để tối ưu tốc độc và khả năng dọn dẹp rác (Garbage Collection)
khi
người chơi ngắt kết nối. Các API được thiết kế theo dạng **Hybrid**:

- **REST API** để thực hiện các thao tác Command (Tạo phòng, Join phòng, Leave phòng) và lấy trạng thái khởi tạo (
  Initial State).
- **WebSocket (STOMP)** để truyền tải các sự kiện thay đổi trạng thái theo thời gian thực (di chuyển quân cờ, cập nhật
  ghế ngồi...).

---

## 2. Cấu trúc dữ liệu trong Redis

> **Kiến trúc Ponytail:** `host`, `white`, `black`, `spectators` trong Redis **chỉ lưu ID (chuỗi)**. Dữ liệu chi tiết (
> avatar, username) được Java Hydrate bằng 1 query DB duy nhất trước khi trả về Frontend — tránh rác JSON và race
> condition.

| Tên Key (`RedisKeys.java`)    | Kiểu              | Mô tả                                                                                  |
|:------------------------------|:------------------|:---------------------------------------------------------------------------------------|
| `room:{roomId}`               | Hash              | Metadata chi tiết của một phòng.                                                       |
| `room:{roomId}:game`          | Hash              | Trạng thái game đang diễn ra (chỉ tồn tại khi phòng ở `IN_PROGRESS`).                 |
| `room:{roomId}:game:moves`    | List              | Danh sách nước đi (UCI notation). Tạo khi game bắt đầu, xóa khi game kết thúc.        |
| `room:idx:lobby`              | ZSet (Sorted Set) | Danh sách phòng hiển thị ở sảnh chờ. `score` là `createdAt` (epoch ms) để phân trang. |
| `room:{roomId}:spectators`    | ZSet              | Danh sách khán giả. `score` là timestamp lúc join (mới nhất lên đầu).                 |
| `room:{roomId}:chat`          | List              | Lịch sử chat trong phòng (tối đa 10 tin nhắn mới nhất).                               |

> **⚠️ Memory Leak:** Các key trên **không có TTL**. Cleanup xảy ra qua luồng nghiệp vụ (host leave → `delete_room.lua`, game over → `end_game.lua`). Nếu server crash trong lúc game đang chạy, các key sẽ bị orphan. Cần set TTL dài (ví dụ 24h) khi tạo room và TTL ngắn (ví dụ 1h) sau khi game kết thúc làm safety net — **chưa implement**.

### Cấu trúc Hash `room:{roomId}`

| Field        | Giá trị                                                                                                                              |
|:-------------|:-------------------------------------------------------------------------------------------------------------------------------------|
| `status`     | Enum string: `WAITING` / `COUNTDOWN` / `IN_PROGRESS` / `FINISHED`                                                                    |
| `hostId`     | ID của host, ví dụ `"1"`                                                                                                             |
| `whiteId`    | ID người cầm quân Trắng, hoặc `""` nếu ghế trống                                                                                     |
| `blackId`    | ID người cầm quân Đen, hoặc `""` nếu ghế trống                                                                                       |
| `whiteReady` | `"true"` hoặc `"false"`                                                                                                              |
| `blackReady` | `"true"` hoặc `"false"`                                                                                                              |
| `startAt`    | Epoch millis (dành cho trạng thái `COUNTDOWN`)                                                                                       |
| `name`       | Tên phòng                                                                                                                            |
| `createdAt`  | Epoch millis                                                                                                                         |
| `settings`   | JSON string của `RoomSettings` (`timeMinutes`, `incrementSeconds`, `variant`, `rated`, `isPrivate`, `chatLocked`, `spectatorLocked`) |

### Cấu trúc Hash `room:{roomId}:game`

| Field                  | Giá trị                                  |
|:-----------------------|:-----------------------------------------|
| `roomId`               | ID của phòng                             |
| `whiteId`              | ID người cầm quân Trắng                  |
| `blackId`              | ID người cầm quân Đen                    |
| `whiteRemainingMillis` | Thời gian còn lại của Trắng (ms)         |
| `blackRemainingMillis` | Thời gian còn lại của Đen (ms)           |
| `incrementMillis`      | Thời gian cộng thêm sau mỗi nước đi (ms) |
| `turn`                 | `"white"` hoặc `"black"`                 |
| `turnStartedAt`        | Epoch millis bắt đầu turn hiện tại       |
| `startAt`              | Epoch millis bắt đầu ván đấu             |
| `fen`                  | FEN hiện tại của bàn cờ                  |

### List `room:{roomId}:game:moves`

Danh sách tất cả nước đi trong ván (UCI notation, ví dụ `"e2e4"`). Mỗi nước đi được `RPUSH` vào cuối sau khi được xác nhận hợp lệ. Dùng để replay ván đấu hoặc lưu lịch sử. Bị `DEL` trong `end_game.lua` khi ván kết thúc.

---

## 3. REST API

### `GET /api/rooms`

Lấy danh sách phòng ở Sảnh (Initial State).

- **Params:** `query` (tìm theo tên, optional), `page` (mặc định `0`), `size` (mặc định `20`)
- **Mô tả:** Nếu có `query`, dùng Lua script (`search_lobby.lua`) để tìm kiếm case-insensitive trực tiếp trong Redis.
  Nếu không có `query`, dùng `ZREVRANGE` lấy trang hiện tại. Kết quả được Hydrate bằng pipeline (1 round-trip).
- **Response:** `{ content: RoomResponse[], page: { size, number, totalElements, totalPages } }`

### `POST /api/rooms`

Tạo phòng mới.

- **Auth:** Bắt buộc. User phải có `PresenceStatus.ONLINE` (đang có WS connection), nếu không → `403`.
- **Guard:** User không được đang ở trong phòng khác (`isInRoom`) → `400`.
- **Body:** `{ name: string, settings?: RoomSettings }`
- **Response:** `RoomResponse`
- **Side effects:**
    - Chạy Lua script `create_room.lua` (atomic): tạo Hash phòng, thêm vào `room:idx:lobby`, update presence host thành
      `IN_ROOM`.
    - Broadcast `ROOM_CREATED` tới `/topic/lobbies`.

### `GET /api/rooms/{roomId}`

Lấy chi tiết phòng (Initial State khi client vào trang phòng).

- **Auth:** Bắt buộc.
- **Guard:** Nếu `settings.isPrivate == true`, chỉ `host`/`white`/`black` mới được xem → `403`.
- **Response:** `RoomResponse` (kèm `spectators[]`).

```json
{
  "roomId": "123-abc",
  "name": "Giao lưu cờ chớp",
  "createdAt": 1718029381000,
  "status": "WAITING",
  "hostId": "1",
  "host": {
    "id": 1,
    "username": "player1",
    "avatarUrl": "..."
  },
  "white": {
    "id": 1,
    "username": "player1",
    "avatarUrl": "..."
  },
  "black": null,
  "spectators": [],
  "settings": {
    "timeMinutes": 3,
    "incrementSeconds": 2,
    "variant": "STANDARD",
    "rated": false,
    "isPrivate": false,
    "chatLocked": false,
    "spectatorLocked": false
  }
}
```

### `POST /api/rooms/{roomId}/join`

Tham gia phòng với vai trò cụ thể.

- **Auth:** Bắt buộc. User phải `ONLINE` và chưa ở trong phòng khác.
- **Body (optional):** `{ "role": "white" | "black" | "spectator" }` — mặc định `"black"` nếu không truyền body.
- **Response:** `RoomResponse` (trạng thái phòng sau khi join).
- **Logic (Lua atomic `join_room.lua`):**

| Code | Điều kiện                                     | Lỗi trả về                          |
|:-----|:----------------------------------------------|:------------------------------------|
| `-1` | Phòng không tồn tại                           | `400 Room not found`                |
| `-2` | `status != WAITING`                           | `400 Room is not accepting players` |
| `-3` | User đã đang ngồi một ghế trong phòng         | `400 Already seated`                |
| `-4` | Ghế được chọn đã có người                     | `400 Seat taken`                    |
| `-5` | `role=spectator` nhưng `spectatorLocked=true` | `400 Spectators not allowed`        |
| `-6` | `role` không hợp lệ                           | `400 Invalid role`                  |

- **Side effects khi thành công:**
    - `white`/`black`: update presence thành `IN_ROOM`, broadcast `PLAYER_JOINED` tới `/topic/room/{roomId}` **và**
      `ROOM_UPDATED` tới `/topic/lobbies`.
    - `spectator`: thêm vào ZSet `room:{roomId}:spectators`, presence **giữ nguyên `ONLINE`** (không set `IN_ROOM`),
      chỉ broadcast `PLAYER_JOINED` tới `/topic/room/{roomId}`.

### `POST /api/rooms/{roomId}/leave`

Rời phòng.

- **Auth:** Bắt buộc. User phải `ONLINE` → `403` nếu không.
- **Response:** `200 OK` (no body).
- **Logic (Lua atomic `leave_room.lua`):**

| Code | Điều kiện                  | Lỗi trả về                                 |
|:-----|:---------------------------|:-------------------------------------------|
| `-1` | Phòng không tồn tại        | `404 Room not found`                       |
| `-2` | `status != WAITING`        | `400 Room is not accepting leave requests` |
| `-3` | User không thuộc phòng này | `403 You are not in this room`             |

- **Side effects theo vai trò:**

| Vai trò               | Lua làm gì                                                                                                                                     | Java làm gì                                                                                                                     |
|:----------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------|
| **Spectator**         | `ZREM room:{roomId}:spectators userId`                                                                                                         | Broadcast `PLAYER_LEFT` tới `/topic/room/{roomId}`                                                                              |
| **Player** (non-host) | `HSET room:{roomId} {role} ""` + `DEL presence:user:{userId}`                                                                                  | Reset presence → `ONLINE`, broadcast `PLAYER_LEFT` + `ROOM_UPDATED`                                                             |
| **Host**              | `DEL room:{roomId}` + `ZREM room:idx:lobby` + `DEL presence host` + `DEL spectators ZSet`; trả về danh sách `white`/`black` + `spectator:{id}` | Reset presence white/black → `ONLINE` (spectators không cần — chưa từng set `IN_ROOM`), broadcast `ROOM_DELETED` tới cả 2 topic |

### `GET /api/rooms/{roomId}/chat`

Lấy lịch sử chat của phòng (tối đa 10 tin nhắn).

- **Auth:** Bắt buộc.
- **Response:** `ChatMessagePayload[]` (chứa `sender`, `message`, `timestamp`).

### `POST /api/games/{roomId}/ready`

Báo cáo sẵn sàng hoặc huỷ sẵn sàng.

- **Auth:** Bắt buộc.
- **Params:** `isReady` (boolean, mặc định `true`)
- **Response:** `200 OK` (no body)
- **Logic (Lua atomic `player_ready.lua`):**
    - Nếu cả hai người `isReady == true`, chuyển phòng sang `COUNTDOWN`, set `startAt` (hiện tại + 3s), báo
      `COUNTDOWN_STARTED` (2).
    - Nếu có người đổi sang `isReady == false` khi đang `COUNTDOWN`, chuyển phòng về `WAITING`, xoá `startAt`, báo
      `COUNTDOWN_CANCELLED` (3).
- **Side effects:**
    - Thành công (bất kể code nào): Broadcast `PLAYER_READY` (kèm `role` và `isReady`) tới `/topic/room/{roomId}`.
    - Khi bắt đầu đếm ngược (2): Lên lịch (Java `TaskScheduler`, key `start:{roomId}`) gọi `startGame` sau 3 giây. Broadcast `COUNTDOWN_STARTED`
      tới `/topic/room/{roomId}` và `ROOM_UPDATED` (status `COUNTDOWN`) tới `/topic/lobbies`.
    - Khi huỷ đếm ngược (3): Huỷ task `startGame`. Broadcast `COUNTDOWN_CANCELLED` tới `/topic/room/{roomId}` và
      `ROOM_UPDATED` (status `WAITING`) tới `/topic/lobbies`.
    - Khi task `startGame` chạy (qua `start_game.lua`): Khởi tạo hash `room:{roomId}:game`, chuyển phòng sang `IN_PROGRESS`.
      Broadcast `GAME_STARTED` (kèm `GameStartedEvent`) tới `/topic/room/{roomId}` và `ROOM_UPDATED` (status `IN_PROGRESS`)
      tới `/topic/lobbies`. **Đồng thời lên lịch turn timer cho white** (key `turn:{roomId}`) theo `timeMinutes` trong settings.

---

## 4. WebSocket Events

### Subscribe `/topic/lobbies` — Cập nhật Sảnh

| `type`         | Khi nào                                                                       | `data`                              |
|:---------------|:------------------------------------------------------------------------------|:------------------------------------|
| `ROOM_CREATED` | Host tạo phòng mới                                                            | `RoomResponse`                      |
| `ROOM_DELETED` | Host leave/disconnect hoặc phòng bị xoá                                       | `{ roomId }`                        |
| `ROOM_UPDATED` | Player join/leave ghế `white`/`black`, hoặc chuyển status (COUNTDOWN, v.v...) | `{ roomId, role?, user?, status? }` |

### Subscribe `/topic/room.{roomId}` — Cập nhật trong Phòng

| `type`                | Khi nào                                        | `data`                                                               |
|:----------------------|:-----------------------------------------------|:---------------------------------------------------------------------|
| `PLAYER_JOINED`       | Có người join ghế hoặc spectate                | `{ role: "white"\|"black"\|"spectator", user: UserSimpleResponse }`  |
| `PLAYER_LEFT`         | Player/spectator rời ghế hoặc disconnect       | `{ role: "white"\|"black"\|"spectator", userId: string }`            |
| `PLAYER_READY`        | Người chơi toggle trạng thái sẵn sàng          | `{ role: "white"\|"black", isReady: boolean }` (`PlayerReadyEvent`)  |
| `ROOM_DELETED`        | Host leave/disconnect khi phòng `WAITING`      | `{ roomId }` — client phải redirect ra lobby                         |
| `CHAT_MESSAGE`        | Có người gửi tin nhắn chat                     | `ChatMessagePayload`                                                 |
| `COUNTDOWN_STARTED`   | Khi cả 2 player sẵn sàng                       | `{ startAt, delayMillis }` (`GameCountDownEvent`)                    |
| `COUNTDOWN_CANCELLED` | Khi có người huỷ sẵn sàng trong lúc đếm ngược  | (No payload)                                                         |
| `GAME_STARTED`        | Khi đếm ngược kết thúc, ván đấu bắt đầu        | `{ whiteId, blackId, turn, fen }` (`GameStartedEvent`)               |
| `MOVE_MADE`           | Sau mỗi nước đi hợp lệ                         | `{ move, color, newTurn, newFen, whiteRemainingMillis, blackRemainingMillis }` (`GameMovedPayload`) |
| `GAME_OVER`           | Khi ván đấu kết thúc (mọi nguyên nhân)         | `{ winner, reason }` (`GameOverPayload`) — xem bảng bên dưới        |

#### `GAME_OVER` — Giá trị `winner` và `reason`

| `winner`           | `reason`      | Mô tả                                         |
|:-------------------|:--------------|:----------------------------------------------|
| `"white"` / `"black"` | `"checkmate"` | Chiếu hết                                  |
| `"white"` / `"black"` | `"timeout"`   | Đối thủ hết giờ (server-side turn timer)   |
| `"white"` / `"black"` | `"resign"`    | Đối thủ đầu hàng *(chưa implement)*        |
| `"draw"`           | `"stalemate"` | Pat                                           |
| `"draw"`           | `"draw"`      | Hòa (50-move rule, insufficient material...) |

> **Server-side turn timer:** Sau mỗi nước đi (và khi game bắt đầu), server lên lịch một `ScheduledFuture` (key `turn:{roomId}`) với thời gian bằng `remainingMillis` của người đến lượt. Nếu hết giờ mà không có nước đi mới, server tự gọi `endGame()` và broadcast `GAME_OVER`. Client **không cần** gửi request để claim timeout.

### Send `/app/room.{roomId}.move` — Gửi nước đi

- **Auth:** Bắt buộc (JWT trong STOMP session).
- **Payload:** `{ "move": "e2e4" }` (`MakeMoveRequest`) — UCI notation.
- **Guard:** Server kiểm tra lượt đi, tính hợp lệ của nước đi (qua `chesslib`), và thời gian còn lại. Nếu sai → không có response, lỗi bị drop (chưa có `@MessageExceptionHandler`).
- **Side effects khi hợp lệ:**
    1. Cập nhật `fen`, `turn`, `{color}RemainingMillis`, `turnStartedAt` vào `room:{roomId}:game`.
    2. `RPUSH` nước đi vào `room:{roomId}:game:moves`.
    3. Broadcast `MOVE_MADE` tới `/topic/room.{roomId}`.
    4. Nếu game over (checkmate/stalemate/draw): gọi `endGame()`, bỏ qua bước 5.
    5. Cancel turn timer cũ, lên lịch turn timer mới cho người tiếp theo.

### Send `/app/room.{roomId}.chat` — Gửi tin nhắn chat

- **Payload:** `{ "message": "Nội dung chat" }` (`ChatSendRequest`)
- **Guard:** Nếu `settings.chatLocked == true`, server sẽ từ chối tin nhắn. Tin nhắn hợp lệ sẽ được lưu vào Redis (tối
  đa 10 tin) và broadcast `CHAT_MESSAGE` tới `/topic/room/{roomId}`.

---

## 5. Disconnect & Cleanup

Luồng xử lý disconnect được thiết kế theo **event-driven pattern** để tách biệt trách nhiệm:

```
SessionDisconnectEvent (Spring WS)
        │
        ▼
PresenceService.applyDisconnect()
  ─ Chạy presence_disconnect.lua (atomic: SREM session, SREM online_users, HGETALL presence)
  ─ Nếu user thực sự went offline (last session):
      ├─ Cập nhật lastSeen → DB
      ├─ Publish UserWentOfflineEvent(userId, presenceData)
      ├─ Xóa presence hash (trừ IN_GAME — giữ để reconnect)
      └─ Broadcast online count
              │
              ▼ (Spring @EventListener, synchronous)
      RoomService.onUserWentOffline()   ← xử lý IN_ROOM cleanup
      GameService.onUserWentOffline()   ← xử lý IN_GAME (future)
```

> Các service tự opt-in bằng `@EventListener` và guard bằng `status` field — `PresenceService` không cần biết domain nào
> tồn tại.

### Host disconnect (`is_host = true`, `status = IN_ROOM`)

Khi host mất kết nối và phòng đang `WAITING`:

1. Chạy `leave_room.lua` (atomic): xóa `room:{roomId}`, `room:idx:lobby`, `presence host` (NOP nếu đã bị xóa bởi
   disconnect Lua), **`room:{roomId}:spectators`**; trả về danh sách white/black + `spectator:{id}`.
2. Reset presence của white/black → `ONLINE` (spectator không set `IN_ROOM` nên không cần reset).
3. Broadcast `ROOM_DELETED` tới cả `/topic/lobbies` và `/topic/room/{roomId}`.

### Non-host player disconnect (`is_host = false`, `status = IN_ROOM`)

1. Chạy `leave_room.lua` (atomic): xóa ghế trong room hash, xóa presence (NOP).
2. Broadcast `PLAYER_LEFT` tới `/topic/room/{roomId}`.
3. Broadcast `ROOM_UPDATED` (với `user: null`) tới `/topic/lobbies`.

### `IN_GAME` disconnect

Giữ nguyên presence hash — chờ reconnect. Turn timer vẫn tiếp tục chạy phía server. Nếu player không reconnect và hết giờ, server tự broadcast `GAME_OVER` (winner là đối thủ, reason `"timeout"`).

> `GameService.onUserWentOffline()` chưa implement — hiện chưa có xử lý đặc biệt khi player `IN_GAME` disconnect ngoài việc turn timer tự xử lý.
