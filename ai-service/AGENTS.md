# AI Service contributor guide

## Commands

- Install dependencies: `make install`
- Run locally: `make run`
- Apply database migrations: `make migrate`
- Run tests: `make test`

`DATABASE_URL` is required for `make migrate` and when `VECTOR_STORE=pgvector`.

## Project conventions

- Keep HTTP handlers in `app/api/routes/`, application logic in `app/services/`, and AI/vector clients in `app/ai/`.
- Read configuration through `get_settings()`; do not read environment variables directly elsewhere.
- Add schema changes as an Alembic revision under `alembic/versions/`. Do not create the `vector` extension at request time.
- PGVector tables use schema `ai_service`; connections must keep `public` in `search_path` because pgvector's `vector` type is normally installed there.
- Unit tests must not call external LLM, Hugging Face, or PostgreSQL services. Mock those boundaries.

## Safety

- Never commit `.env` or API keys.
- Do not modify or drop data outside the `ai_service` schema in an application migration.
