# Đặc tả API: Quản lý Phòng chơi (Rooms Spec)

Tài liệu này đặc tả chi tiết về cấu trúc dữ liệu, luồng hoạt động và các API/WebSocket Topic liên quan đến hệ thống quản
lý Phòng chơi (Lobby & Rooms).

---

## 1. Tổng quan & Kiến trúc

Hệ thống sử dụng **Redis** làm database chính cho Room để tối ưu tốc độ và khả năng dọn dẹp rác (Garbage Collection) khi
người chơi ngắt kết nối.
Các API được thiết kế theo dạng **Hybrid**:

- **REST API** để thực hiện các thao tác Command (Tạo phòng, Chơi game) và lấy trạng thái khởi tạo (Initial State).
- **WebSocket (STOMP)** để truyền tải các sự kiện thay đổi trạng thái theo thời gian thực (Event Broadcasting) như di
  chuyển quân cờ.

---

## 2. Cấu trúc dữ liệu trong Redis

Hệ thống tách biệt Metadata của phòng, Danh sách các phòng, và Danh sách người xem ra các keys khác nhau để tối ưu hóa
tần suất đọc/ghi độc lập.
*Lưu ý (Kiến trúc chuẩn Ponytail):* Thông tin người dùng (`host`, `white`, `black`, `spectators`) trong Redis **chỉ lưu
ID (chuỗi)** để tránh rác JSON và tránh Race Condition. Dữ liệu chi tiết (Avatar, Username) sẽ được Java tự động
Hydrate (ráp nối) bằng 1 câu query DB duy nhất trước khi trả về Frontend.

| Tên Key (`RedisKeys.java`) | Kiểu (Type)       | Mô tả                                                                               |
|:---------------------------|:------------------|:------------------------------------------------------------------------------------|
| `room:{roomId}`            | Hash              | Chứa metadata chi tiết của một phòng.                                               |
| `room:idx:lobby`           | ZSet (Sorted Set) | Danh sách các phòng đang hiển thị ở sảnh chờ. `score` là timestamp (để phân trang). |
| `room:{roomId}:spectators` | ZSet              | Danh sách người đang xem (Spectators). `score` là timestamp lúc join.               |

### Cấu trúc chi tiết của Hash `room:{roomId}`

- `status`: Trạng thái phòng sử dụng Enum (`WAITING`, `IN_PROGRESS`, `FINISHED`).
- `host`: ID của host (Ví dụ: `"1"`).
- `settings`: Cấu hình phòng (JSON String: `timeMinutes`, `incrementSeconds`, `variant`, `rated`, `isPrivate`...).
- `createdAt`: Thời điểm tạo phòng (epoch millis).
- `name`: Tên phòng (chuỗi rỗng nếu không nhập).
- `white`: ID của người chơi cầm quân Trắng (hoặc rỗng nếu ghế trống).
- `black`: ID của người chơi cầm quân Đen (hoặc rỗng nếu ghế trống).

---

## 3. Quản lý Sảnh (Lobby) & Các API

### 3.1. REST API

#### Lấy danh sách phòng ở Sảnh (Initial State)

- **Method:** `GET /api/rooms`
- **Params:** `page` (mặc định 0), `size` (mặc định 20)
- **Mô tả:** Lấy danh sách 20 phòng mới nhất đang ở trạng thái hiển thị trên Sảnh. (Sử dụng ZREVRANGE trên
  `room:idx:lobby`).
- **Response:** Object JSON chứa mảng `content` (thông tin các phòng) và `page` (thông tin phân trang). Các ID (`host`,
  `white`, `black`) sẽ được tự động Hydrate thành Object.

#### Lấy chi tiết một phòng chơi (Initial State khi vào phòng)

- **Method:** `GET /api/rooms/{roomId}`
- **Mô tả:** Lấy toàn bộ thông tin của phòng bao gồm cài đặt, thành viên (`host`, `white`, `black`) và khán giả (
  `spectators`).
- **Authorization:** Nếu `isPrivate == true`, chỉ những user đang ngồi trong ghế (`host`, `white`, `black`) mới được
  phép fetch thông tin. Nếu không sẽ trả về `403 FORBIDDEN`.
- **Response Example:**

```json
{
  "roomId": "123-abc",
  "name": "Giao lưu cờ chớp",
  "createdAt": "1718029381000",
  "status": "WAITING",
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
  "black": {
    "id": 2,
    "username": "player2",
    "avatarUrl": "..."
  },
  "spectators": [
    {
      "id": 3,
      "username": "viewer1",
      "avatarUrl": "..."
    }
  ],
  "settings": {
    "timeMinutes": 3,
    "incrementSeconds": 2,
    "variant": "STANDARD",
    "rated": false,
    "isPrivate": false
  }
}
```

#### Tạo phòng mới

- **Method:** `POST /api/rooms`
- **Body:** `CreateRoomRequest` (chứa `name` và `settings`).
- **Điều kiện:** Người dùng phải đang **Online (có kết nối WebSocket)** (`PresenceStatus.ONLINE`) thì mới được phép gọi
  API này. Nếu không, hệ thống trả về mã `403 FORBIDDEN`. Trạng thái Presence của host sẽ chuyển sang `IN_ROOM`.
- **Response:** `{ "roomId": "xxx-yyy-zzz" }`

### 3.2. Realtime Updates (WebSocket)

Client cần `subscribe` vào topic **`/topic/lobbies`** để nhận các cập nhật Real-time trên sảnh mà không cần polling lại
REST API.

#### Event: Phòng mới được tạo

```json
{
  "type": "ROOM_CREATED",
  "data": {
    "roomId": "123-abc",
    "host": {
      "id": 1,
      "username": "player1"
    },
    ...
  }
}
```

#### Event: Phòng bị xoá (Host thoát)

```json
{
  "type": "ROOM_DELETED",
  "data": {
    "roomId": "123-abc"
  }
}
```

---

## 4. Dọn rác tự động & Disconnect Logic

Ứng dụng quản lý ngắt kết nối qua cơ chế tập trung tại `PresenceService` và các Lua Scripts (`presence_disconnect.lua`):

1. **Giữ kết nối IN_GAME:** Nếu user rớt mạng khi trạng thái là `IN_GAME`, hệ thống **GIỮ NGUYÊN** hash presence để chờ
   reconnect hoặc xử lý timeout (xử thua).
2. **Dọn dẹp IN_ROOM:** Nếu user rớt mạng khi đang ngồi chờ (`IN_ROOM`):
    - Nếu user đó là `host`, phòng sẽ bị huỷ bỏ (xoá khỏi `room:idx:lobby`, xoá `room:{roomId}`).
    - Gửi broadcast `ROOM_DELETED` ra `/topic/lobbies`.
    - Tiến hành xoá bỏ Presence Hash để dọn dẹp bộ nhớ triệt để.
