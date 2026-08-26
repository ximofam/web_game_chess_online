# AI Service contributor guide

## Commands

- Install dependencies: `make install`
- Run locally: `make run`
- Apply database migrations: `make migrate`
- Run tests: `make test`

`DATABASE_URL` is required for `make migrate` and PGVector store operations.

## Project conventions

- Keep HTTP handlers in `app/api/routes/`, application logic in `app/services/`, and AI/vector clients in `app/ai/`.
- Read configuration through `get_settings()`; do not read environment variables directly elsewhere.
- Add schema changes as an Alembic revision under `alembic/versions/`. Do not create the `vector` extension at request time.
- PGVector tables use schema `ai_service`; connections must keep `public` in `search_path` because pgvector's `vector` type is normally installed there.
- Unit tests must not call external LLM, Hugging Face, or PostgreSQL services. Mock those boundaries.

## Safety

- Never commit `.env` or API keys.
- Do not modify or drop data outside the `ai_service` schema in an application migration.

## Agent skills

### Issue tracker

GitHub Issues via `gh` CLI (`git@github.com:ximofam/web_game_chess_online.git`). See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical 5-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`CONTEXT.md` at root, ADRs in `docs/adr/`). See `docs/agents/domain.md`.

### Handoff convention

- Always save handoff documents to `.scratch/handoffs/handoff-YYYYMMDD-HHMMSS-<topic>.md` in the current workspace.
- Replace `YYYYMMDD-HHMMSS` with the current local timestamp and `<topic>` with a concise kebab-case summary of the task.
- Structure handoff files with:
  1. Header: Timestamp, Branch, Latest Commit, Working Tree state.
  2. `## 1. Executive Summary & Context`
  3. `## 2. Key Architecture Decisions`
  4. `## 3. Work Accomplished & File Map` (with clickable `file:///` links)
  5. `## 4. Immediate Next Steps for Next Session`
  6. `## 5. Suggested Skills for Next Agent`

