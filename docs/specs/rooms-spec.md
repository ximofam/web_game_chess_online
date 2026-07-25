# Đặc tả API: Quản lý Phòng chơi (Rooms Spec)

Tài liệu này đặc tả chi tiết về cấu trúc dữ liệu, luồng hoạt động và các API/WebSocket Topic liên quan đến hệ thống quản
lý Phòng chơi (Lobby & Rooms).

---

## 1. Tổng quan & Kiến trúc

Hệ thống sử dụng **Redis** làm database chính cho Room để tối ưu tốc độ và khả năng dọn dẹp rác (Garbage Collection) khi
người chơi ngắt kết nối. Các API được thiết kế theo dạng **Hybrid**:

- **REST API** để thực hiện các thao tác Command (Tạo phòng, Join phòng) và lấy trạng thái khởi tạo (Initial State).
- **WebSocket (STOMP)** để truyền tải các sự kiện thay đổi trạng thái theo thời gian thực (di chuyển quân cờ, cập nhật
  ghế ngồi...).

---

## 2. Cấu trúc dữ liệu trong Redis

> **Kiến trúc Ponytail:** `host`, `white`, `black`, `spectators` trong Redis **chỉ lưu ID (chuỗi)**. Dữ liệu chi tiết (
> avatar, username) được Java Hydrate bằng 1 query DB duy nhất trước khi trả về Frontend — tránh rác JSON và race
> condition.

| Tên Key (`RedisKeys.java`) | Kiểu              | Mô tả                                                                                 |
|:---------------------------|:------------------|:--------------------------------------------------------------------------------------|
| `room:{roomId}`            | Hash              | Metadata chi tiết của một phòng.                                                      |
| `room:idx:lobby`           | ZSet (Sorted Set) | Danh sách phòng hiển thị ở sảnh chờ. `score` là `createdAt` (epoch ms) để phân trang. |
| `room:{roomId}:spectators` | ZSet              | Danh sách khán giả. `score` là timestamp lúc join (mới nhất lên đầu).                 |

### Cấu trúc Hash `room:{roomId}`

| Field       | Giá trị                                                                                                                              |
|:------------|:-------------------------------------------------------------------------------------------------------------------------------------|
| `status`    | Enum string: `WAITING` / `IN_PROGRESS` / `FINISHED`                                                                                  |
| `host`      | ID của host, ví dụ `"1"`                                                                                                             |
| `white`     | ID người cầm quân Trắng, hoặc `""` nếu ghế trống                                                                                     |
| `black`     | ID người cầm quân Đen, hoặc `""` nếu ghế trống                                                                                       |
| `name`      | Tên phòng                                                                                                                            |
| `createdAt` | Epoch millis                                                                                                                         |
| `settings`  | JSON string của `RoomSettings` (`timeMinutes`, `incrementSeconds`, `variant`, `rated`, `isPrivate`, `chatLocked`, `spectatorLocked`) |

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

- **Side effects khi thành công:**
    - `white`/`black`: update presence thành `IN_ROOM`, broadcast `PLAYER_JOINED` tới `/topic/room/{roomId}` **và**
      `ROOM_UPDATED` tới `/topic/lobbies`.
    - `spectator`: thêm vào ZSet `room:{roomId}:spectators`, presence giữ nguyên `ONLINE`, chỉ broadcast `PLAYER_JOINED`
      tới `/topic/room/{roomId}`.

---

## 4. WebSocket Events

### Subscribe `/topic/lobbies` — Cập nhật Sảnh

| `type`         | Khi nào                              | `data`                                       |
|:---------------|:-------------------------------------|:---------------------------------------------|
| `ROOM_CREATED` | Host tạo phòng mới                   | `RoomResponse`                               |
| `ROOM_DELETED` | Host disconnect hoặc phòng bị xoá    | `{ roomId }`                                 |
| `ROOM_UPDATED` | Player join ghế `white` hoặc `black` | `{ roomId, role, user: UserSimpleResponse }` |

### Subscribe `/topic/room/{roomId}` — Cập nhật trong Phòng

| `type`          | Khi nào                         | `data`                                                              |
|:----------------|:--------------------------------|:--------------------------------------------------------------------|
| `PLAYER_JOINED` | Có người join ghế hoặc spectate | `{ role: "white"\|"black"\|"spectator", user: UserSimpleResponse }` |

---

## 5. Disconnect & Cleanup

Tập trung tại `PresenceService` + `presence_disconnect.lua`:

1. **`IN_GAME`**: Giữ nguyên presence hash để chờ reconnect / xử lý timeout thua cuộc.
2. **`IN_ROOM` (host)**: Xoá `room:{roomId}` và `room:idx:lobby` entry → broadcast `ROOM_DELETED` tới `/topic/lobbies` →
   xoá presence hash.
3. **`IN_ROOM` (non-host)**: *(YAGNI — chưa implement, cần xử lý clear ghế)*.
4. **`ONLINE`**: Xoá presence hash.
