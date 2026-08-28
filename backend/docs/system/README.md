# Tài liệu Thiết kế & Kiến trúc Hệ thống (System Design & Specifications)
## Dự án: Web Game Chess Online & Community Platform

Thư mục này chứa toàn bộ các tài liệu đặc tả thiết kế hệ thống phần mềm, bao gồm **Use Case**, **Domain Models (Class Diagrams)** và **Sequence Diagrams**, được trực quan hóa 100% bằng **Mermaid Diagrams** hiển thị trực tiếp trong Markdown trên GitHub / IDE mà không cần build trung gian.

---

## 📑 Danh mục Tài liệu Hệ thống

### 1. [Đặc tả Use Case Hệ thống (Use Case Specification)](./usecase/README.md)
- **Tài liệu chi tiết:** [**`docs/system/usecase/README.md`**](./usecase/README.md)
- **Nội dung:**
  - Tổng quan kiến trúc công nghệ & 4 nhóm Actor chính (`GUEST`, `USER`, `ADMIN`, Background Schedulers & AI Worker).
  - 4 Sơ đồ Use Case phân hệ hiển thị trực tiếp qua Mermaid:
    - **01. Phân hệ Xác thực & Quản lý Người dùng**
    - **02. Phân hệ Phòng chơi & Trận đấu Cờ vua**
    - **03. Phân hệ Diễn đàn, Hình ảnh & Kiểm duyệt AI**
    - **04. Phân hệ Thông báo & Realtime**
  - Đặc tả chi tiết 58 ca sử dụng (Mục tiêu, Luồng chính, Luồng phụ, Ràng buộc kỹ thuật).
  - Ma trận phân quyền (Actor Permission Matrix) & Ma trận truy vết kỹ thuật (Traceability Matrix).

---

### 2. [Sơ đồ Thực thể & Mô hình Miền (Domain Models Specification)](./class/README.md)
- **Tài liệu chi tiết:** [**`docs/system/class/README.md`**](./class/README.md)
- **Nội dung:**
  - **00. Sơ đồ Miền Thực thể Toàn hệ thống (System Domain Model):** Mô tả toàn bộ JPA Entities, Embeddables, Redis In-Memory Models, Enums và các mối quan hệ đa miền bằng Mermaid Class Diagram.
  - Các sơ đồ miền chi tiết theo từng phân hệ:
    - **01. User & Identity Domain Model**
    - **02. Chess Domain Model**
    - **03. Forum Domain Model**
    - **04. Notification Domain Model**

---

### 3. [Sơ đồ Tuần tự Hệ thống (Sequence Diagrams Specification)](./sequence/README.md)
- **Tài liệu chi tiết:** [**`docs/system/sequence/README.md`**](./sequence/README.md)
- **Nội dung:** Đặc tả 21 luồng tương tác tuần tự thời gian thực hiển thị trực tiếp bằng Mermaid Sequence Diagram trên 6 phân hệ cốt lõi:
  - **Xác thực & Quản lý Phiên:** Đăng ký User/Guest, Đăng nhập, Token Rotation, Đăng xuất.
  - **Người dùng & Hiện diện:** Cập nhật hồ sơ, Tải ảnh đại diện, Vòng đời WebSocket Presence.
  - **Phòng chơi & Sảnh chờ:** Tạo phòng cờ, Tham gia & Đổi ghế, Rời phòng & Bàn giao chủ phòng / Xóa phòng.
  - **Trận đấu Cờ vua:** Sẵn sàng & Đếm ngược, Đi cờ & Đồng hồ, Hết giờ Server Timer, Đề nghị hòa.
  - **Diễn đàn & Kiểm duyệt:** Tạo bài viết & Spring AI kiểm duyệt, Vòng đời ảnh Tiptap & Cron dọn dẹp, Bình luận lồng nhau, Thích bài viết/bình luận.
  - **Thông báo Realtime:** RabbitMQ Consumer lưu PostgreSQL và đẩy STOMP `/user/queue/notifications`.
