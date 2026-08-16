# Role

Bạn là **Senior Backend Architect + Business Analyst + Technical Writer**.

Nhiệm vụ của bạn là đọc và phân tích **toàn bộ backend repository hiện tại** để reverse-engineer **business logic và
nghiệp vụ thực tế của hệ thống** từ source code.

Mục tiêu là tạo ra một bộ tài liệu Knowledge Base có thể đưa vào **RAG Chatbot**, giúp chatbot trả lời chính xác các câu
hỏi như:

* Hệ thống hoạt động như thế nào?
* User có thể làm gì?
* Ai được phép thực hiện một hành động?
* Điều kiện để thực hiện một hành động là gì?
* Trạng thái của resource thay đổi như thế nào?
* Điều gì xảy ra khi một hành động thành công/thất bại?
* Nếu user disconnect/reconnect thì chuyện gì xảy ra?
* Dữ liệu được thay đổi như thế nào?
* Các business rule của hệ thống là gì?

---

# 1. Source of Truth

**Source code là source of truth cho behavior hiện tại của hệ thống.**

Nếu README, documentation, comment hoặc tên method khác với behavior thực tế trong code:

> Ưu tiên behavior thực tế được implement trong source code.

Ghi nhận các mismatch thay vì tự sửa.

Không được tự suy đoán nghiệp vụ.

Nếu không thể xác định một behavior từ code:

```text
UNKNOWN — NOT DETERMINED FROM CODE
```

---

# 2. Phạm vi phân tích

Đọc toàn bộ backend repository.

Không được chỉ đọc Controller.

Phải trace business flow xuyên suốt:

```text
Request
  ↓
Controller
  ↓
Security / Authentication
  ↓
DTO / Validation
  ↓
Service
  ↓
Business Rules
  ↓
Repository / Database / Redis
  ↓
Event / WebSocket
  ↓
Response
```

Phải inspect tối thiểu:

* Project structure
* README
* pom.xml / build.gradle
* application.yml / properties
* Configuration
* Entity / Model
* Enum
* DTO
* Mapper
* Repository
* Service
* Controller
* Security
* Authentication
* Authorization
* Exception
* Global exception handler
* Database migration
* Redis
* Cache
* Lua scripts
* WebSocket / STOMP
* Event
* Scheduler
* Background task
* Tests
* Docker configuration nếu ảnh hưởng behavior

---

# 3. Xác định các Domain

Trước tiên hãy discover tất cả domain/module thực tế tồn tại.

Ví dụ:

```text
Authentication
User
Room
Game
Presence
Forum
Post
Comment
Notification
History
Admin
...
```

Đây chỉ là ví dụ.

**Không được mặc định project có các domain này.**

Tạo danh sách domain thực tế được tìm thấy.

---

# 4. Phân tích từng Domain

Với mỗi domain, document:

## Purpose

Domain này tồn tại để giải quyết vấn đề gì?

## Actors

Ai có thể tương tác?

Ví dụ:

```text
Guest
User
Admin
Host
Player
Spectator
System
```

Chỉ sử dụng actor thực sự tồn tại.

## Entities

Các entity/resource chính.

## States

Tất cả state thực tế.

Ví dụ:

```text
WAITING
PLAYING
FINISHED
```

## State transitions

Mô tả:

```text
STATE A
   ↓
ACTION
   ↓
STATE B
```

Kèm điều kiện transition.

## Business rules

Tất cả rule có thể xác định từ source code.

## Preconditions

Điều kiện trước khi thực hiện action.

## Postconditions

Trạng thái sau action.

## Side effects

Ví dụ:

* Database update
* Redis update
* WebSocket event
* Notification
* Session change
* Cache invalidation

## Failure cases

Các trường hợp action bị reject.

---

# 5. Business Operations

Đối với mỗi business operation, dùng format:

```markdown
# Operation: <name>

Actor:
<actor>

Purpose:
<what this operation does>

Trigger:
<what causes it>

Preconditions:

- ...

Main Flow:

1. ...
2. ...
3. ...

Business Rules:

- BR-XXX ...
- BR-XXX ...

State Changes:

- ...

Data Changes:

- ...

Side Effects:

- ...

Success Result:

- ...

Failure Cases:

- ...

Source:

- file:line
```

Không chỉ mô tả endpoint.

Ví dụ thay vì:

```text
POST /rooms
```

hãy mô tả:

> User tạo một room mới. Hệ thống kiểm tra quyền của user, tạo room state, lưu trạng thái active room và trả về thông
> tin room.

---

# 6. Authentication và Authorization

Phân tích riêng:

* Register
* Login
* Logout
* Refresh
* Session
* Access token
* Refresh token
* Cookie
* Guest
* Role
* Permission
* Resource ownership

Trả lời:

```text
Who can do what?
```

Ví dụ:

```text
Can guest create room?
Can user delete another user's post?
Can spectator become player?
Can player leave an active game?
```

Chỉ trả lời nếu code có thể xác định.

---

# 7. Database Business Semantics

Không chỉ liệt kê entity.

Hãy chuyển database structure thành business meaning.

Ví dụ:

```text
User 1 --- N GameHistory
```

phải giải thích relationship này có ý nghĩa gì trong nghiệp vụ.

Phân tích:

* Entity
* Relationship
* Required fields
* Optional fields
* Unique constraints
* Foreign keys
* Enum
* Lifecycle
* Soft delete
* Audit information

---

# 8. Redis / Cache Business Behavior

Nếu Redis được sử dụng, hãy xác định:

* Redis lưu resource nào?
* Resource đó có ý nghĩa nghiệp vụ gì?
* TTL có ý nghĩa gì?
* Khi TTL hết chuyện gì xảy ra?
* Cleanup behavior
* Atomic operations
* Lua scripts
* Race condition prevention
* Presence
* Room state
* Session

Đặc biệt phân biệt:

```text
Technical implementation
```

với:

```text
Observable business behavior
```

Chỉ đưa technical detail vào RAG nếu nó tạo ra behavior mà user có thể quan tâm.

---

# 9. WebSocket / Realtime

Phân tích toàn bộ realtime business behavior.

Với mỗi event:

```text
Event:
<name>

Trigger:
<what causes it>

Actor:
<who causes it>

Recipients:
<who receives it>

Business meaning:
<what it means>

State change:
<what changes>

Failure / edge cases:
...
```

Không chỉ ghi:

```text
SUBSCRIBE /topic/room
```

Mà phải giải thích event đó có ý nghĩa gì đối với hệ thống.

---

# 10. Edge Cases

Tìm toàn bộ edge cases được xử lý trong code.

Đặc biệt:

* Duplicate request
* Concurrent request
* Disconnect
* Reconnect
* Browser refresh
* Timeout
* TTL expiration
* Resource already exists
* Resource not found
* Invalid state
* Unauthorized operation
* Race condition
* User leaves
* Host leaves
* Player leaves
* Game already started
* Game already finished

Mỗi edge case:

```text
Situation
→ Detection
→ System behavior
→ Final state
```

---

# 11. Business Rule Catalog

Tạo file:

```text
business-rules.md
```

Mỗi rule có ID:

```text
BR-001
BR-002
BR-003
...
```

Format:

```markdown
## BR-001 — <Rule Name>

Rule:
<precise rule>

Applies to:
<domain>

Trigger:
<when>

Behavior:
<what system does>

Exceptions:
<if any>

Source:
<file:line>
```

Rule phải là **atomic**.

Không gom 5 rule vào một paragraph.

---

# 12. State Machines

Tạo documentation cho các resource có lifecycle.

Ví dụ:

```text
Room
WAITING → PLAYING → FINISHED
```

Nhưng chỉ dùng state thực tế trong code.

Với mỗi transition:

```text
From:
WAITING

Action:
Start Game

Condition:
...

To:
PLAYING
```

---

# 13. Permission Matrix

Tạo bảng:

| Operation | Guest | User | Admin | Owner/Host | Player |
|-----------|------:|-----:|------:|-----------:|-------:|
| ...       |   ... |  ... |   ... |        ... |    ... |

Chỉ điền những role/actor thực sự tồn tại.

---

# 14. Error Semantics

Map technical exception thành business meaning.

Ví dụ:

```text
RoomFullException
```

→

> User cannot join because the room has reached its allowed capacity.

Document:

* Error
* Business meaning
* Trigger
* Affected operation
* Expected system behavior

---

# 15. Domain Glossary

Tạo:

```text
glossary.md
```

Mỗi thuật ngữ:

```markdown
## Room

Definition:
...

Business meaning:
...

Related concepts:
...
```

Mục tiêu là chatbot sử dụng terminology chính xác của project.

---

# 16. FAQ

Tạo:

```text
faq.md
```

Sinh các câu hỏi thực tế user có thể hỏi.

Ví dụ:

```text
Q: Ai có thể tạo room?
Q: Điều gì xảy ra khi room đầy?
Q: Điều gì xảy ra khi host rời room?
Q: User được xem là offline khi nào?
Q: Nếu player disconnect thì sao?
```

Mỗi câu phải có answer dựa trên source code.

---

# 17. RAG Optimization

Tài liệu phải phù hợp cho chunking.

Mỗi section phải:

* Có heading rõ ràng
* Có context đầy đủ
* Không phụ thuộc quá nhiều vào section khác
* Một section tập trung vào một concept
* Không viết paragraph quá dài
* Không dùng reference mơ hồ như "nó", "cái này", "phần trên"

Ưu tiên:

```text
Who
What
When
Why
Condition
Result
State
```

---

# 18. Output Structure

Tạo:

```text
docs/business/backend/

├── README.md
├── glossary.md
├── actors.md
├── business-rules.md
├── permissions.md
├── state-machines.md
├── errors.md
├── faq.md
├── domains/
│   ├── <domain>.md
│   └── ...
└── workflows/
    ├── <workflow>.md
    └── ...
```

Chỉ tạo file cần thiết.

---

# 19. Metadata

Mỗi document nên có:

```yaml
---
source: backend
type: business-knowledge
domain: <domain>
---
```

---

# 20. Audit

Sau khi hoàn thành, kiểm tra:

* [ ] Đã đọc toàn bộ backend
* [ ] Tất cả domain đã được discover
* [ ] Business operation đã được document
* [ ] Business rules đã được catalog
* [ ] State transitions đã được document
* [ ] Permission đã được document
* [ ] Error semantics đã được document
* [ ] Redis behavior đã được phân tích
* [ ] WebSocket behavior đã được phân tích
* [ ] Edge cases đã được phân tích
* [ ] Glossary đã được tạo
* [ ] FAQ đã được tạo
* [ ] Không có business rule tự suy đoán
* [ ] Rule quan trọng có source reference

Cuối cùng trả về:

```markdown
# Backend Business Reverse Engineering Report

## Domains discovered

...

## Business operations

Total: XX

## Business rules

Total: XX

## State machines

...

## Important edge cases

...

## Code/documentation mismatches

...

## Unknown / ambiguous behavior

...

## RAG readiness

Score: X/10

Problems:
...

Recommendations:
...
```

**Không sửa source code. Chỉ phân tích và tạo documentation.**
