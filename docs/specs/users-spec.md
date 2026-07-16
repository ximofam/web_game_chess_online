# Đặc tả API: Users (Users API Specification)

Tài liệu này đặc tả chi tiết về các API liên quan đến quản lý thông tin người dùng (User Profile, Avatar).

---

## 1. Tổng quan & Xác thực

Tất cả các API dưới đây có tiền tố (base path) là `/api/users`.
Các API cập nhật thông tin yêu cầu xác thực bằng Access Token thông qua Header `Authorization: Bearer <token>`.
Riêng vai trò `GUEST` bị hạn chế không được phép gọi một số API như cập nhật thông tin cá nhân hoặc tải lên avatar.

---

## 2. Danh sách các API Endpoints

```mermaid
classDiagram
    class ApiUserController {
        +getUserByUsername(String username) ResponseEntity~UserResponse~
        +getMyProfile(Long userId) ResponseEntity~UserDetailResponse~
        +updateMyProfile(Long userId, UpdateUserProfileRequest request) ResponseEntity~UserDetailResponse~
        +uploadMyAvatar(Long userId, MultipartFile file) ResponseEntity~ApiResponse~
    }
```

### 2.1. Lấy thông tin người dùng bằng Username (Get User by Username)

- **Endpoint:** `GET /api/users/{username}`
- **Mô tả:** Lấy thông tin công khai của một người dùng dựa trên `username`.
- **Headers:** Không yêu cầu xác thực.
- **Path Parameters:**
    - `username` (String): Tên đăng nhập của người dùng cần tìm kiếm.
- **Response:**
    - **Success:** `200 OK`
      ```json
      {
        "id": 1,
        "username": "example_user",
        "email": "user@example.com",
        "avatarUrl": "https://example.com/avatar.jpg",
        "profile": {
          "fullName": "Nguyen Van A",
          "gender": "MALE",
          "dateOfBirth": "16/07/2000"
        }
      }
      ```
    - **Error (Not Found):** `404 Not Found` (nếu không tìm thấy người dùng).

---

### 2.2. Lấy thông tin cá nhân của người dùng hiện tại (Get My Profile)

- **Endpoint:** `GET /api/users/me`
- **Mô tả:** Lấy thông tin chi tiết của người dùng đang đăng nhập dựa trên JWT token.
- **Headers:**
    - `Authorization: Bearer <Access_Token>` (Bắt buộc)
- **Response:**
    - **Success:** `200 OK`
      ```json
      {
        "id": 1,
        "username": "example_user",
        "email": "user@example.com",
        "avatarUrl": "https://example.com/avatar.jpg",
        "role": "USER",
        "profile": {
          "fullName": "Nguyen Van A",
          "gender": "MALE",
          "dateOfBirth": "16/07/2000"
        }
      }
      ```
    - **Error:** `401 Unauthorized`.

---

### 2.3. Cập nhật thông tin cá nhân (Update My Profile)

- **Endpoint:** `PATCH /api/users/me`
- **Mô tả:** Cập nhật thông tin cá nhân của người dùng hiện tại.
- **Headers:**
    - `Authorization: Bearer <Access_Token>` (Bắt buộc)
- **Quyền truy cập:** Không cho phép tài khoản có vai trò `GUEST`.
- **Request Body (JSON):** `UpdateUserProfileRequest`
  ```json
  {
    "fullName": "Nguyen Van B",
    "gender": "MALE",
    "dateOfBirth": "20/08/2000"
  }
  ```
  *Ràng buộc:*
    - `dateOfBirth`: Định dạng `dd/MM/yyyy`.
- **Response:**
    - **Success:** `200 OK`
      ```json
      {
        "id": 1,
        "username": "example_user",
        "email": "user@example.com",
        "avatarUrl": "https://example.com/avatar.jpg",
        "role": "USER",
        "profile": {
          "fullName": "Nguyen Van B",
          "gender": "MALE",
          "dateOfBirth": "20/08/2000"
        }
      }
      ```
    - **Error (Validation failed):** `400 Bad Request`.
    - **Error (Unauthorized / Forbidden):** `401 Unauthorized` / `403 Forbidden` (đối với GUEST).

---

### 2.4. Tải lên ảnh đại diện (Upload My Avatar)

- **Endpoint:** `PATCH /api/users/me/avatar`
- **Mô tả:** Tải lên ảnh đại diện mới của người dùng và lưu trên dịch vụ Cloudinary.
- **Headers:**
    - `Authorization: Bearer <Access_Token>` (Bắt buộc)
    - `Content-Type: multipart/form-data`
- **Quyền truy cập:** Không cho phép tài khoản có vai trò `GUEST`.
- **Request Parameters (Multipart Form Data):**
    - `file` (MultipartFile): Tệp hình ảnh cần tải lên.
- **Response:**
    - **Success:** `200 OK`
      ```json
      {
        "avatarUrl": "https://res.cloudinary.com/.../image.jpg"
      }
      ```
    - **Error (Bad Request):** `400 Bad Request` (nếu không có tệp hoặc tệp không hợp lệ).
    - **Error (Unauthorized / Forbidden):** `401 Unauthorized` / `403 Forbidden` (đối với GUEST).

---

## 3. Định dạng lỗi & Xử lý lỗi chung

*(Tham chiếu cấu trúc lỗi chung từ Auth API Specification)*
