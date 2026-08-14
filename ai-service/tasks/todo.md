# TODO: RAG Chat Implementation

## Task 1 — Dependencies
- [ ] Task: Thêm `langgraph`, `langgraph-checkpoint-postgres`, `sqlalchemy[asyncio]` vào `requirements.txt`
  - Acceptance: `make install` chạy thành công; `import langgraph` không lỗi
  - Verify: `make install && python -c "import langgraph; import sqlalchemy.ext.asyncio"`
  - Files: `requirements.txt`

## Task 2 — Migration 0002: chat_messages
- [ ] Task: Tạo Alembic migration tạo bảng `ai_service.chat_messages`
  - Acceptance: `make migrate` chạy thành công; bảng tồn tại trong schema `ai_service`
  - Verify: `make migrate` exit 0; psql query xác nhận bảng
  - Files: `alembic/versions/20260814_0002_create_chat_messages.py`

## Task 3 — SQLAlchemy model + async DB session
- [ ] Task: Tạo `AiChatMessage` model và `get_db()` async dependency
  - Acceptance: Model map đúng bảng `ai_service.chat_messages`; `get_db()` yield `AsyncSession`
  - Verify: `make test` pass (unit test mock AsyncSession)
  - Files: `app/models/chat.py`, `app/core/db.py`

## Task 4 — LangGraph: State + Nodes
- [ ] Task: Implement `RagState`, `analyze_question`, `retrieve`, `generate_rag`, `generate_direct`
  - Acceptance: Mỗi node nhận đúng state keys, trả về đúng output keys; mock test pass
  - Verify: `make test` pass
  - Files: `app/graph/state.py`, `app/graph/nodes.py`

## Task 5 — LangGraph: Graph builder
- [ ] Task: Compile graph với conditional routing và attach `PostgresSaver` checkpointer
  - Acceptance: Graph route "system" → retrieve→generate_rag; "chess" → generate_direct
  - Verify: Unit test với mock checkpointer; invoke trực tiếp (không stream) xác nhận routing
  - Files: `app/graph/builder.py`

## Task 6 — Chat service + schema
- [ ] Task: Tạo `ChatRequest` schema và `save_message()` service function
  - Acceptance: `save_message()` insert đúng role/content/question_type
  - Verify: `make test` pass
  - Files: `app/schemas/chat.py`, `app/services/chat_service.py`

## Task 7 — SSE endpoint
- [ ] Task: Implement `POST /api/chat/{session_id}` với SSE streaming
  - Acceptance: Stream token, kết thúc bằng `event: done\ndata: [DONE]`; lưu user message trước, assistant message sau
  - Verify: `make test` pass; manual curl test stream
  - Files: `app/api/routes/chat.py`, `app/api/router.py`

## Task 8 — Smoke test tổng hợp
- [ ] Task: Test end-to-end: 2 lượt hỏi liên tiếp cùng session_id, verify rewrite dùng đúng history
  - Acceptance: Lượt 2 rewrite refer đúng context lượt 1
  - Verify: Manual test hoặc integration test với mock LLM deterministic
  - Files: `tests/`
