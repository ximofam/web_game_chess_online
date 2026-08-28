# Tài liệu Sơ đồ Thực thể & Mô hình Miền (Domain Models & Entities)
## Dự án: Web Game Chess Online & Community Platform

Tài liệu này tổng hợp toàn bộ các **Sơ đồ Miền Thực thể (Domain Models & Entities)** của hệ thống dưới dạng **Mermaid Class Diagram** hiển thị trực tiếp trong Markdown.

---

## 📑 Danh mục Sơ đồ Miền Thực thể

1. [**00. Sơ đồ Miền Thực thể Toàn hệ thống (System Domain Model)**](#1-sơ-đồ-miền-thực-thể-toàn-hệ-thống-system-domain-model)
2. [**01. User & Identity Domain Model**](#2-user--identity-domain-model)
3. [**02. Chess Domain Model (Rooms & Game Entities)**](#3-chess-domain-model-rooms--game-entities)
4. [**03. Forum Domain Model (Posts, Comments & Image Lifecycle)**](#4-forum-domain-model-posts-comments--image-lifecycle)
5. [**04. Notification Domain Model**](#5-notification-domain-model)

---

## 1. Sơ đồ Miền Thực thể Toàn hệ thống (System Domain Model)

Sơ đồ tổng quan mô tả toàn bộ cấu trúc dữ liệu, quan hệ giữa các thực thể (Kế thừa, Kết tập, Thành phần, Liên kết 1-1, 1-N) trên 4 phân hệ chính: **User & Identity**, **Chess**, **Forum**, và **Notification**.

```mermaid
classDiagram
    class BaseModel {
        <<Abstract>>
        #UUID id
        #Instant createdAt
        #Instant updatedAt
        +prePersist() void
        +equals(Object) boolean
        +hashCode() int
    }

    class SoftDeleteModel {
        <<Abstract>>
        #Instant deletedAt
        +isDeleted() boolean
    }

    class User {
        -String username
        -String email
        -String passwordHash
        -UserRole role
        -boolean isActive
        -boolean isLocked
        -Instant lastSeen
        -UserProfile profile
    }

    class UserProfile {
        <<Embeddable>>
        -String avatarPublicId
        -String avatarUrl
        -String fullName
        -Gender gender
        -LocalDate dateOfBirth
    }

    class RefreshSession {
        <<Redis>>
        -String userId
        -String userRole
    }

    class Game {
        -UUID whiteId
        -UUID blackId
        -String pgn
        -Instant startTime
        -Instant endTime
        -int timeMinutes
        -int incrementSeconds
        -String variant
        -boolean rated
        -GameStatus status
        -GameResult result
        -ResultReason resultReason
        -GameSource source
    }

    class Room {
        <<Redis Hash>>
        -String roomId
        -String name
        -RoomStatus status
        -String hostId
        -String whiteId
        -String blackId
        -boolean whiteReady
        -boolean blackReady
        -Long startAt
        -Long createdAt
        -RoomSettings settings
    }

    class RoomSettings {
        <<JSON>>
        -int timeMinutes
        -int incrementSeconds
        -String variant
        -boolean rated
        -boolean isPrivate
        -boolean chatLocked
        -boolean spectatorLocked
    }

    class Post {
        -User author
        -String title
        -String content
        -PostStatus status
        -long viewCount
        -ApprovalInfo approvalInfo
    }

    class ApprovalInfo {
        <<Embeddable>>
        -User approvedBy
        -Instant approvedAt
        -String approvalNote
    }

    class Comment {
        -Post post
        -User author
        -String content
        -Comment parent
        -List~Comment~ replies
        -List~CommentLike~ likes
    }

    class PostLike {
        -User user
        -Post post
        -boolean isActive
    }

    class CommentLike {
        -User user
        -Comment comment
        -boolean isActive
    }

    class PostImage {
        -UUID postId
        -UUID uploaderId
        -String url
        -String publicId
        -ImageStatus status
    }

    class Notification {
        -User recipient
        -User sender
        -String title
        -String message
        -NotificationType type
        -Map metadata
        -boolean isRead
    }

    BaseModel <|-- SoftDeleteModel
    SoftDeleteModel <|-- User
    BaseModel <|-- Game
    SoftDeleteModel <|-- Post
    SoftDeleteModel <|-- Comment
    BaseModel <|-- PostLike
    BaseModel <|-- CommentLike
    BaseModel <|-- PostImage
    BaseModel <|-- Notification

    User *-- "1" UserProfile : embeds
    Post *-- "1" ApprovalInfo : embeds
    Room *-- "1" RoomSettings : contains

    User "1" <-- "0..*" Game : white
    User "1" <-- "0..*" Game : black
    User "1" <-- "0..*" Post : author
    Post "1" <-- "0..*" Comment : post
    User "1" <-- "0..*" Comment : author
    Comment "0..1" <-- "0..*" Comment : parent
    User "1" <-- "0..*" PostLike : user
    Post "1" <-- "0..*" PostLike : post
    User "1" <-- "0..*" CommentLike : user
    Comment "1" <-- "0..*" CommentLike : comment
    User "1" <-- "0..*" PostImage : uploader
    Post "0..1" <-- "0..*" PostImage : post
    User "1" <-- "0..*" Notification : recipient
    User "0..1" <-- "0..*" Notification : sender
```

---

## 2. User & Identity Domain Model

Mô tả thực thể người dùng `User`, hồ sơ cá nhân nhúng `UserProfile`, mô hình phiên làm việc trên Redis `RefreshSession` và các Enums định danh (`UserRole`, `Gender`, `PresenceStatus`, `TokenType`).

```mermaid
classDiagram
    class BaseModel {
        <<Abstract>>
        #UUID id
        #Instant createdAt
        #Instant updatedAt
    }

    class SoftDeleteModel {
        <<Abstract>>
        #Instant deletedAt
        +isDeleted() boolean
    }

    class User {
        -String username
        -String email
        -String passwordHash
        -UserRole role
        -boolean isActive
        -boolean isLocked
        -Instant lastSeen
        -UserProfile profile
        +getProfile() UserProfile
        +isEnable() boolean
    }

    class UserProfile {
        <<Embeddable>>
        -String avatarPublicId
        -String avatarUrl
        -String fullName
        -Gender gender
        -LocalDate dateOfBirth
    }

    class RefreshSession {
        <<Redis>>
        -String userId
        -String userRole
    }

    class UserRole {
        <<Enumeration>>
        SUPERUSER
        ADMIN
        USER
        GUEST
    }

    class Gender {
        <<Enumeration>>
        MALE
        FEMALE
    }

    class PresenceStatus {
        <<Enumeration>>
        ONLINE
        IN_ROOM
        PLAYING
        OFFLINE
    }

    class TokenType {
        <<Enumeration>>
        ACCESS
        REFRESH
        GUEST
    }

    BaseModel <|-- SoftDeleteModel
    SoftDeleteModel <|-- User
    User *-- "1" UserProfile : embeds
    User --> UserRole
    UserProfile --> Gender
```

---

## 3. Chess Domain Model (Rooms & Game Entities)

Mô tả thực thể lưu trữ ván cờ `Game` trong PostgreSQL (lưu biên bản PGN, thời gian, kết quả), cấu trúc phòng chơi thời gian thực trên Redis Hash `Room`, cấu hình phòng `RoomSettings` và các Enums luật cờ.

```mermaid
classDiagram
    class BaseModel {
        <<Abstract>>
        #UUID id
        #Instant createdAt
        #Instant updatedAt
    }

    class Game {
        -UUID whiteId
        -UUID blackId
        -String pgn
        -Instant startTime
        -Instant endTime
        -int timeMinutes
        -int incrementSeconds
        -String variant
        -boolean rated
        -GameStatus status
        -GameResult result
        -ResultReason resultReason
        -GameSource source
    }

    class Room {
        <<Redis Hash>>
        -String roomId
        -String name
        -RoomStatus status
        -String hostId
        -String whiteId
        -String blackId
        -boolean whiteReady
        -boolean blackReady
        -Long startAt
        -Long createdAt
        -RoomSettings settings
    }

    class RoomSettings {
        <<JSON>>
        -int timeMinutes
        -int incrementSeconds
        -String variant
        -boolean rated
        -boolean isPrivate
        -boolean chatLocked
        -boolean spectatorLocked
    }

    class GameStatus {
        <<Enumeration>>
        WAITING
        COUNTDOWN
        IN_PROGRESS
        FINISHED
    }

    class GameResult {
        <<Enumeration>>
        WHITE_WIN
        BLACK_WIN
        DRAW
    }

    class ResultReason {
        <<Enumeration>>
        CHECKMATE
        RESIGN
        TIMEOUT
        STALEMATE
        DRAW_AGREEMENT
        DRAW
    }

    class GameSource {
        <<Enumeration>>
        ROOM
        LOBBY
    }

    class RoomStatus {
        <<Enumeration>>
        WAITING
        COUNTDOWN
        IN_PROGRESS
        FINISHED
    }

    class PlayerRole {
        <<Enumeration>>
        WHITE
        BLACK
        SPECTATOR
    }

    BaseModel <|-- Game
    Game --> GameStatus
    Game --> GameResult
    Game --> ResultReason
    Game --> GameSource
    Room *-- "1" RoomSettings : contains
    Room --> RoomStatus
```

---

## 4. Forum Domain Model (Posts, Comments & Image Lifecycle)

Mô tả thực thể bài viết `Post`, thông tin kiểm duyệt nhúng `ApprovalInfo`, bình luận phân cấp cây `Comment`, lượt thích `PostLike`/`CommentLike`, vòng đời hình ảnh đính kèm `PostImage` và các Enums trạng thái.

```mermaid
classDiagram
    class BaseModel {
        <<Abstract>>
        #UUID id
        #Instant createdAt
        #Instant updatedAt
    }

    class SoftDeleteModel {
        <<Abstract>>
        #Instant deletedAt
        +isDeleted() boolean
    }

    class Post {
        -User author
        -String title
        -String content
        -PostStatus status
        -long viewCount
        -ApprovalInfo approvalInfo
        +incrementViewCount() void
    }

    class ApprovalInfo {
        <<Embeddable>>
        -User approvedBy
        -Instant approvedAt
        -String approvalNote
        +isApproved() boolean
    }

    class Comment {
        -Post post
        -User author
        -String content
        -Comment parent
        -List~Comment~ replies
        -List~CommentLike~ likes
        +isReply() boolean
        +isRootComment() boolean
    }

    class PostLike {
        -User user
        -Post post
        -boolean isActive
    }

    class CommentLike {
        -User user
        -Comment comment
        -boolean isActive
    }

    class PostImage {
        -UUID postId
        -UUID uploaderId
        -String url
        -String publicId
        -ImageStatus status
    }

    class PostStatus {
        <<Enumeration>>
        DRAFT
        PENDING
        APPROVED
        DENIED
    }

    class ImageStatus {
        <<Enumeration>>
        ORPHAN
        ATTACHED
    }

    BaseModel <|-- SoftDeleteModel
    SoftDeleteModel <|-- Post
    SoftDeleteModel <|-- Comment
    BaseModel <|-- PostLike
    BaseModel <|-- CommentLike
    BaseModel <|-- PostImage

    Post --> PostStatus
    Post *-- "1" ApprovalInfo : embeds
    PostImage --> ImageStatus
```

---

## 5. Notification Domain Model

Mô tả thực thể thông báo người dùng `Notification` và Enum phân loại thông báo `NotificationType`.

```mermaid
classDiagram
    class BaseModel {
        <<Abstract>>
        #UUID id
        #Instant createdAt
        #Instant updatedAt
    }

    class Notification {
        -User recipient
        -User sender
        -String title
        -String message
        -NotificationType type
        -Map metadata
        -boolean isRead
    }

    class NotificationType {
        <<Enumeration>>
        POST_LIKE
        COMMENT_LIKE
        POST_COMMENT
        COMMENT_REPLY
        SYSTEM_MESSAGE
        GAME_INVITE
    }

    BaseModel <|-- Notification
    Notification --> NotificationType
```
