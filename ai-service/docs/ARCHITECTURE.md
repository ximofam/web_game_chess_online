# Architecture

## Scope

This service exposes a FastAPI AI Service API powering intelligent chess Q&A, FIDE rules retrieval, and platform support. It supports local Chroma and PostgreSQL pgvector for vector storage, along with LangGraph checkpointer persistence.

## Request Flow

```text
POST /api/chat/{session_id} ──> app/api/routes/chat.py
                                 └─> app/services/chat_service.py (ConversationEngine)
                                      ├─> Dual Persistence (ai_chat_messages + checkpoints)
                                      ├─> LangGraph Pipeline (app/rag/builder.py)
                                      │    ├─> contextualize_question
                                      │    ├─> route_question
                                      │    ├─> retrieve_docs (app/rag/retriever.py)
                                      │    ├─> generate_rag / generate_general / no_context_answer
                                      │    └─> summarize_memory
                                      └─> Background Auto-Titling (generate_session_title_task)

POST /api/rag/documents ─────> app/api/routes/rag.py
                                 └─> app/services/rag_service.py (Knowledge Ingestion)
                                      ├─> app/rag/ingestion/fide_splitter.py (FIDE legal hierarchy)
                                      └─> app/rag/retriever.py (Vector Store)

GET /api/health ─────────────> app/api/routes/health.py
```

## Module Design & Seams

- **ConversationEngine** ([`app/services/chat_service.py`](file:///home/ximofam/MyCodes/DoAnNganhOUCS2302/my_graduation_project/ai-service/app/services/chat_service.py)): Deep module encapsulating session creation, dual-state recording, stateful graph execution, and background titling behind a clean `send_message` interface.
- **Knowledge Ingestion** ([`app/services/rag_service.py`](file:///home/ximofam/MyCodes/DoAnNganhOUCS2302/my_graduation_project/ai-service/app/services/rag_service.py)): Consolidated ingestion module managing document chunking, collection truncation, and batch indexing for both Markdown docs and FIDE Laws of Chess PDFs.
- **Graph Seam** ([`app/rag/builder.py`](file:///home/ximofam/MyCodes/DoAnNganhOUCS2302/my_graduation_project/ai-service/app/rag/builder.py)): Exposes a swappable checkpointer seam (`AsyncPostgresSaver` in production, `MemorySaver` in tests/local).

## Configuration

`app/core/config.py` loads `.env` through `pydantic-settings`.

| Setting | Purpose |
| --- | --- |
| `VECTOR_STORE` | `chroma` (default) or `pgvector` |
| `DATABASE_URL` | Required for pgvector, session persistence, and migrations |
| `EMBEDDING_PROVIDER` | `huggingface` or `openai` |
| `LLM_PROVIDER` | `groq` or `openai` |
| `VISION_PROVIDER` | `groq` or `openai` (falls back to `LLM_PROVIDER` if omitted) |
| `GROQ_VISION_MODEL` | Vision model on Groq (default: `llama-3.2-11b-vision-preview`) |
| `OPENAI_VISION_MODEL` | Vision model on OpenAI (default: `gpt-4o-mini`) |
| `RETRIEVAL_SCORE_THRESHOLD` | Relevance score cutoff (default `0.5`) |


## PostgreSQL and Migrations

Alembic owns database bootstrap. Running `make migrate` applies revisions in `alembic/versions/`:
- `ai_service` schema creation and database-wide `vector` extension.
- `ai_chat_sessions` and `ai_chat_messages` tables.
- Auto-managed LangGraph `checkpoints` and `checkpoint_writes` tables inside `ai_service` schema.

