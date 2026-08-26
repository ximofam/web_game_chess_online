## Lua Scripting Standards
- Before writing a Lua script, if a function needs to return an error, define and use the error from `lua/utils/status.lua` instead of hardcoding it.
- Reusable functions or logic must be extracted into utility files in the `utils` directory to avoid code duplication.
- Whenever a new error code is added to `lua/utils/status.lua`, you MUST also update `src/main/java/com/ximofam/graduation_project/common/utils/LuaErrorHandler.java` to map the new error code to the appropriate Java exception.

## Redis Guidelines
- Whenever a new Redis key pattern or static key is introduced, you MUST define it centrally in `src/main/java/com/ximofam/graduation_project/common/utils/RedisKeys.java` rather than hardcoding string literals in services or controllers.

## Agent skills

### Issue tracker

Issues are tracked on GitHub via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical triage roles mapped 1:1 (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context (`CONTEXT.md` and `docs/adr/` at repo root). See `docs/agents/domain.md`.