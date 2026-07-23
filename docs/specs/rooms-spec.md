# Đặc tả API: Quản lý Phòng chơi (Rooms Spec)

Tài liệu này đặc tả chi tiết về cấu trúc dữ liệu, luồng hoạt động và các API/WebSocket Topic liên quan đến hệ thống quản lý Phòng chơi (Lobby & Rooms).

---

## 1. Tổng quan & Kiến trúc

Hệ thống sử dụng **Redis** làm database chính cho Room để tối ưu tốc độ và khả năng dọn dẹp rác (Garbage Collection) khi người chơi ngắt kết nối.
Các API được thiết kế theo dạng **Hybrid**: 
- **REST API** để thực hiện các thao tác Command (Tạo phòng, Chơi game) và lấy trạng thái khởi tạo.
- **WebSocket (STOMP)** để truyền tải các sự kiện thay đổi trạng thái theo thời gian thực (Event Broadcasting).

---

## 2. Cấu trúc dữ liệu trong Redis

Hệ thống tách biệt Metadata của phòng, Danh sách các phòng, và Danh sách người xem ra các keys khác nhau để tối ưu hóa tần suất đọc/ghi độc lập:

| Tên Key | Kiểu (Type) | Mô tả |
| :--- | :--- | :--- |
| `room:{roomId}` | Hash | Chứa metadata chi tiết của một phòng. |
| `rooms:lobby` | ZSet (Sorted Set) | Danh sách các phòng đang hiển thị ở sảnh chờ. `score` là timestamp (để phân trang). |
| `user:{userId}:rooms` | Set | Danh sách ID các phòng mà user này đang làm host. Dùng để dọn rác tự động. |
| `room:{roomId}:spectators` | ZSet | Danh sách người đang xem (Spectators). `score` là timestamp lúc join. |

### Cấu trúc chi tiết của Hash `room:{roomId}`
- `status`: Trạng thái phòng (`WAITING`, `IN_PROGRESS`, `FINISHED`).
- `host`: Thông tin host (JSON String của đối tượng `UserSimpleResponse`).
- `settings`: Cấu hình phòng (JSON String: `timeMinutes`, `incrementSeconds`, `variant`, `rated`, `isPrivate`...).
- `createdAt`: Thời điểm tạo phòng (epoch millis).
- `name`: Tên phòng (chuỗi rỗng nếu không nhập).
- `white`: JSON String của đối tượng `UserSimpleResponse` cầm quân Trắng (hoặc rỗng nếu ghế trống).
- `black`: JSON String của đối tượng `UserSimpleResponse` cầm quân Đen (hoặc rỗng nếu ghế trống).

---

## 3. Quản lý Sảnh (Lobby) & Các API

### 3.1. REST API

#### Lấy danh sách phòng ở Sảnh (Initial State)
- **Method:** `GET /api/rooms`
- **Params:** `page` (mặc định 0), `size` (mặc định 20)
- **Mô tả:** Lấy danh sách 20 phòng mới nhất đang ở trạng thái hiển thị trên Sảnh. (Sử dụng ZREVRANGE trên `rooms:lobby`).
- **Response:** Object JSON chứa mảng `content` (thông tin các phòng) và `page` (thông tin phân trang). Các trường JSON String trong phòng như `host`, `settings`, `white`, `black` được parse ngược thành Object lồng nhau, ghế trống sẽ trả về `null`.

**Response Example:**
```json
{
  "content": [
    {
      "roomId": "123-abc",
      "name": "Giao lưu cờ chớp",
      "createdAt": "1718029381000",
      "status": "WAITING",
      "host": {
        "id": 1,
        "username": "player1"
      },
      "white": {
        "id": 1,
        "username": "player1"
      },
      "black": null,
      "settings": {
        "timeMinutes": 3,
        "incrementSeconds": 2,
        "variant": "STANDARD",
        "rated": false,
        "isPrivate": false
      }
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

#### Tạo phòng mới
- **Method:** `POST /api/rooms`
- **Body:** `CreateRoomRequest` (chứa `name` và `settings`).
- **Điều kiện:** Người dùng phải đang **Online (có kết nối WebSocket)** thì mới được phép gọi API này. Nếu không, hệ thống trả về mã `403 FORBIDDEN`.
- **Response:** `{ "roomId": "xxx-yyy-zzz" }`

### 3.2. Realtime Updates (WebSocket)

Client cần `subscribe` vào topic **`/topic/lobbies`** để nhận các cập nhật Real-time trên sảnh mà không cần polling lại REST API.

#### Event: Phòng mới được tạo
```json
{
  "type": "ROOM_CREATED",
  "data": {
    "roomId": "123-abc",
    "host": { "id": 1, "username": "player1" },
    "name": "Giao lưu cờ chớp",
    "createdAt": 1718029381000,
    "settings": { 
      "timeMinutes": 3, 
      "incrementSeconds": 2, 
      "variant": "STANDARD", 
      "rated": false 
    },
    "white": { "id": 1, "username": "player1" },
    "black": null,
    "status": "WAITING"
  }
}
```

#### Event: Phòng bị xoá (Host thoát)
```json
{
  "type": "ROOM_DELETED",
  "data": { "roomId": "123-abc" }
}
```

#### Event: Trạng thái phòng thay đổi (Vào game)
*(Dự kiến)*
```json
{
  "type": "ROOM_UPDATED",
  "data": { "roomId": "123-abc", "status": "IN_PROGRESS" }
}
```

---

## 4. Dọn rác tự động (Lazy Cleanup)

Vì ứng dụng là một hệ thống Multi-device WebSocket, việc kết nối có thể bị ngắt (Mất mạng, đóng tab). Hệ thống sử dụng cơ chế **Lazy Cleanup** thông qua file Script Lua `cleanup_rooms.lua`:

1. Khi hệ thống `PresenceService` phát hiện một người chơi hoàn toàn disconnect (session cuối cùng đóng).
2. Hệ thống gọi Lua Script xoá bỏ toàn bộ các phòng thuộc sỡ hữu của `userId` đó mà đang ở trạng thái `WAITING`.
3. Trả về danh sách các `roomId` vừa bị xoá.
4. `PresenceService` phát tín hiệu `ROOM_DELETED` ra `/topic/lobbies` để tất cả client khác đang ở Sảnh lập tức gỡ bỏ phòng này khỏi UI.
