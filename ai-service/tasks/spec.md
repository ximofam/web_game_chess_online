# Spec: RAG Chat — AI Service

## Objective

Thêm tính năng chat AI cho hệ thống chess web. ai-service (FastAPI + LangChain +
LangGraph) nhận câu hỏi từ client, phân loại và trả lời theo hai nhánh:

- **system** — câu hỏi về platform/tính năng trang web → RAG (retrieve tài liệu nội bộ rồi generate)
- **chess** — câu hỏi về cờ vua nói chung → LLM trả lời trực tiếp, không cần retrieval

Câu trả lời được **stream qua SSE**. Lịch sử chat lưu vào Postgres (schema `ai_service`,
bảng `chat_messages`). Ngữ cảnh nhiều lượt hỏi được duy trì bởi **LangGraph
PostgresSaver** checkpointer — mỗi chat session là một `thread_id` riêng.

**Success criteria:**
- `POST /api/chat/{session_id}` stream token qua SSE, kết thúc bằng `event: done`
- Câu hỏi về platform được route đúng qua RAG; câu về chess route thẳng LLM
- Rewrite chuẩn: câu hỏi follow-up dùng đúng ngữ cảnh của lượt trước
- `chat_messages` ghi đúng thứ tự: user message trước, assistant message sau khi stream xong
- `question_type` được lưu vào row của assistant
- Tất cả bảng nằm đúng schema `ai_service`
- `make test` pass — không gọi LLM/DB thật trong tests

---

## Tech Stack

| Thành phần | Thư viện |
|---|---|
| Web framework | FastAPI + Uvicorn |
| AI orchestration | LangChain, LangGraph |
| LLM | Groq (default) / OpenAI |
| Embeddings | HuggingFace (default) / OpenAI |
| Vector store | Chroma (default) / PGVector |
| Graph checkpointer | `langgraph-checkpoint-postgres` (PostgresSaver) |
| DB driver | `psycopg[binary]` v3 (đã có) |
| DB migrations | Alembic |
| ORM | SQLAlchemy (async) |
| Config | pydantic-settings |

**Packages cần thêm vào `requirements.txt`:**
```
langgraph
langgraph-checkpoint-postgres
sqlalchemy[asyncio]
```

---

## Commands

```bash
make install    # pip install -r requirements.txt
make run        # uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
make migrate    # alembic upgrade head   (cần DATABASE_URL)
make test       # python -m pytest tests/
```

---

## Project Structure (sau khi implement)

```
app/
├── ai/
│   ├── embeddings.py       # get_embeddings() — đã có
│   ├── llm.py              # get_llm() — đã có
│   ├── prompts.py          # RAG_PROMPT — đã có
│   └── retriever.py        # get_vector_store(), retrieve() — đã có
├── api/
│   ├── router.py           # aggregate routers
│   └── routes/
│       ├── health.py       # GET /health
│       ├── rag.py          # POST /rag/documents, POST /rag/ask — đã có
│       └── chat.py         # POST /chat/{session_id}  ← MỚI
├── core/
│   ├── config.py           # get_settings() — đã có
│   └── db.py               # async engine + get_db() dependency  ← MỚI
├── graph/
│   ├── __init__.py
│   ├── state.py            # RagState TypedDict  ← MỚI
│   ├── nodes.py            # analyze_question, retrieve, generate_rag, generate_direct  ← MỚI
│   └── builder.py          # compile graph + attach checkpointer  ← MỚI
├── models/
│   ├── __init__.py
│   └── chat.py             # AiChatMessage SQLAlchemy model  ← MỚI
├── schemas/
│   ├── rag.py              # đã có
│   └── chat.py             # ChatRequest Pydantic schema  ← MỚI
└── services/
    ├── rag_service.py      # đã có
    └── chat_service.py     # save_message()  ← MỚI
alembic/
└── versions/
    ├── 20260814_0001_create_ai_service_schema.py  # đã có
    └── 20260814_0002_create_chat_messages.py      # MỚI
tasks/
├── spec.md      ← file này
└── todo.md
```

---

## LangGraph — Graph Design

### State

```python
# app/graph/state.py
from typing import TypedDict, Literal
from langchain_core.messages import BaseMessage

class RagState(TypedDict):
    original_question: str
    rewritten_question: str
    question_type: Literal["system", "chess"]
    chat_history: list[BaseMessage]
    documents: list[str]
    answer: str
```

### Nodes

| Node | LLM call? |
|---|---|
| `analyze_question` | ✅ structured output — rewrite + classify trong 1 lần gọi |
| `retrieve` | ❌ |
| `generate_rag` | ✅ |
| `generate_direct` | ✅ |

### Flow

```
analyze_question
    ├─ question_type == "system" → retrieve → generate_rag → END
    └─ question_type == "chess"  → generate_direct → END
```

### Checkpointer

`PostgresSaver` với `DATABASE_URL` có `search_path=ai_service`. Tự tạo
`checkpoints`, `checkpoint_writes` — không viết migration tay.

`thread_id = str(session_id)` — liên kết logic, không có FK vật lý.

> **ponytail:** `chat_history: []` trong state — dựa vào checkpointer tự nối context.
> Ceiling: history quá dài → vượt context window LLM.
> Upgrade path: query N tin nhắn gần nhất từ `chat_messages` theo `session_id`.

---

## Database

### Migration chain (Alembic)

| Revision | Nội dung |
|---|---|
| `20260814_0001` | CREATE SCHEMA ai_service + CREATE EXTENSION vector (đã có) |
| `20260814_0002` | CREATE TABLE ai_service.chat_messages (cần tạo) |

### Schema `chat_messages`

| Column | Type | |
|---|---|---|
| `id` | INTEGER | PK, autoincrement |
| `session_id` | INTEGER | NOT NULL, INDEX |
| `role` | VARCHAR(20) | NOT NULL — "user" / "assistant" |
| `question_type` | VARCHAR(20) | NULLABLE — "system" / "chess" |
| `content` | TEXT | NOT NULL |
| `created_at` | TIMESTAMP | DEFAULT now() |

Không có FK sang bảng backend chính.

---

## Testing Strategy

- Framework: pytest (đã có)
- Không gọi LLM, HuggingFace, PostgreSQL thật
- Unit test graph nodes: mock LLM, mock retriever
- Unit test `save_message`: mock AsyncSession
- Integration test endpoint: mock `astream_events`, mock DB

---

## Boundaries

**Always:**
- Config chỉ đọc qua `get_settings()`
- Schema change → Alembic revision
- Unit tests không gọi service ngoài
- Bảng ai-service chỉ trong schema `ai_service`

**Ask first:**
- Thêm dependency ngoài spec
- Thay đổi format SSE
- Thêm column vào `chat_messages`

**Never:**
- Commit `.env` hoặc API key
- DROP/ALTER bảng ngoài schema `ai_service`
- Gọi LLM thật trong unit test
- Tạo `vector` extension trong request handler

---

## Open Questions (đã giải quyết)

| # | Câu hỏi | Quyết định |
|---|---|---|
| 1 | psycopg2 hay psycopg v3? | **psycopg v3** — nhất quán với codebase |
| 2 | `session_id` là gì? | **ID của chat session** — 1 user nhiều session |
| 3 | `chat_history` trong state? | **`[]`** — dựa vào checkpointer |
| 4 | Migration 0001? | **Đã có** — chỉ cần migration 0002 |
