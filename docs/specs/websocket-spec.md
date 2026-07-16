# Đặc tả API: WebSocket & Realtime Notification (WebSocket Spec)

Tài liệu này đặc tả chi tiết về cấu hình, luồng xác thực, cơ chế truyền tải thời gian thực (Realtime) qua WebSocket sử dụng giao thức STOMP trong dự án.

---

## 1. Tổng quan & Kiến trúc

Hệ thống sử dụng **WebSocket** kết hợp với giao thức **STOMP (Simple Text Orientated Messaging Protocol)** và broker **RabbitMQ (STOMP Broker Relay)** để đẩy các sự kiện thời gian thực (như thông báo) tới client.

- **WebSocket Endpoint:** `/ws`
- **Thư viện hỗ trợ Client:** SockJS (được bật mặc định để hỗ trợ fallback khi trình duyệt không hỗ trợ WebSocket thuần).
- **STOMP Broker Relay:** Chuyển tiếp tin nhắn thông qua RabbitMQ STOMP plugin với các prefix `/topic` và `/queue`.
- **Heartbeat:** 10,000ms (cả gửi và nhận).

---

## 2. Luồng xác thực kết nối (Connection Authentication)

Việc xác thực được thực hiện ngay tại thời điểm client gửi frame `CONNECT`. Client bắt buộc phải đính kèm JWT Access Token hợp lệ.

### Định dạng Frame CONNECT từ Client

```stomp
CONNECT
accept-version:1.1,1.2
heart-beat:10000,10000
Authorization:Bearer <Access_Token>

^@
```

*Lưu ý:* Header `Authorization` là một native header của STOMP Frame.

### Phía Server xử lý:
1. Interceptor của [WebSocketConfig](file:///home/ximofam/MyCodes/DoAnNganhOUCS2302/my_graduation_project/src/main/java/com/ximofam/graduation_project/configs/WebSocketConfig.java) sẽ chặn và kiểm tra frame `CONNECT`.
2. Lấy giá trị của header `Authorization` (dưới dạng `Bearer <token>`).
3. Giải mã và kiểm tra tính hợp lệ của token qua `TokenService` (chỉ chấp nhận token type là `"access"`).
4. Nếu hợp lệ: Thiết lập thông tin xác thực (`userId`, `authorities`) vào phiên WebSocket.
5. Nếu không hợp lệ hoặc thiếu header: Trả về lỗi `MessageDeliveryException` (Invalid JWT token / Missing or invalid Authorization header) và từ chối kết nối.

---

## 3. Cấu hình Prefix & Destinations

| Prefix / Destination | Loại | Mô tả |
| :--- | :--- | :--- |
| `/app` | Application Prefix | Các tin nhắn Client gửi lên Server xử lý (Controller `@MessageMapping`). |
| `/user` | User Destination Prefix | Prefix dùng để gửi tin nhắn riêng biệt (Private message) tới một User cụ thể. |
| `/topic` | Broker Destination | Thường dùng cho cơ chế Publish/Subscribe (Pub/Sub) - tin nhắn gửi tới nhiều người. |
| `/queue` | Broker Destination | Thường dùng cho tin nhắn Point-to-Point (P2P). |

---

## 4. Nhận thông báo thời gian thực (Realtime Notifications)

Khi có thông báo mới (được xử lý thông qua hàng đợi RabbitMQ bởi [NotificationEventListener](file:///home/ximofam/MyCodes/DoAnNganhOUCS2302/my_graduation_project/src/main/java/com/ximofam/graduation_project/notifications/NotificationEventListener.java)), hệ thống sẽ gửi tin nhắn đến client thông qua WebSocket.

### 4.1. Đăng ký nhận thông báo (Subscribe)
Client cần thực hiện kết nối WebSocket thành công và thực hiện subscribe vào destination cá nhân sau:

- **Destination:** `/user/queue/notifications`

*Phía dưới hạ tầng (Under the hood):* Spring STOMP sẽ tự động ánh xạ `/user/queue/notifications` thành `/queue/notifications-user<session-id>` để đảm bảo chỉ người dùng sở hữu phiên đó mới nhận được tin nhắn.

### 4.2. Định dạng dữ liệu nhận được (Payload)
Mỗi thông báo gửi qua kênh WebSocket sẽ có định dạng JSON khớp với `NotificationResponse`:

```json
{
  "id": 123,
  "sender": {
    "id": 2,
    "username": "sender_username",
    "avatarUrl": "https://example.com/avatar.jpg"
  },
  "type": "POST_LIKE",
  "title": "Lượt thích mới",
  "message": "sender_username đã thích bài viết của bạn.",
  "metadata": {
    "postId": 456
  },
  "createdAt": "2026-07-16T17:02:47.000Z",
  "read": false
}
```

---

## 5. Giới hạn truyền tải (Transport Limits)

Để đảm bảo hiệu năng và tránh tấn công từ chối dịch vụ (DoS):
- **Message Size Limit:** Tối đa `128 KB` cho mỗi tin nhắn.
- **Send Buffer Size Limit:** Tối đa `512 KB` cho bộ đệm gửi.
- **Send Time Limit:** Tối đa `20s` (20,000 ms) thời gian chờ gửi tin.
