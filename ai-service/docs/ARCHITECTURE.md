# Architecture

## Scope

This service exposes a small FastAPI RAG API. It stores and retrieves documents through either local Chroma or PostgreSQL with pgvector.

## Request flow

```text
POST /api/rag/documents ─┐
POST /api/rag/ask ───────┼─> app/api/routes/rag.py
                         └─> app/services/rag_service.py
                              ├─> app/ai/retriever.py (vector store)
                              ├─> app/ai/embeddings.py (embedding provider)
                              └─> app/ai/llm.py + prompts.py (answer generation)
```

- `POST /api/rag/documents` embeds and stores one document.
- `POST /api/rag/ask` retrieves the nearest documents, builds context, then asks the configured LLM.
- `GET /api/health` returns service liveness.

## Configuration

`app/core/config.py` loads `.env` through `pydantic-settings`.

| Setting | Purpose |
| --- | --- |
| `VECTOR_STORE` | `chroma` (default) or `pgvector` |
| `DATABASE_URL` | Required for pgvector and migrations |
| `EMBEDDING_PROVIDER` | `huggingface` or `openai` |
| `LLM_PROVIDER` | `groq` or `openai` |

## PostgreSQL and migrations

Alembic owns database bootstrap. Running `make migrate` applies the initial revision, which creates schema `ai_service` and enables the database-wide `vector` extension.

PGVector creates its own `langchain_pg_collection` and `langchain_pg_embedding` tables on first use. Connections use `search_path=ai_service,public`: the tables are created in `ai_service`, while `public` exposes the pgvector `vector` type. The current embedding column is unconstrained `VECTOR`; the default `sentence-transformers/all-MiniLM-L6-v2` produces 384-dimensional embeddings.
