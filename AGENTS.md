# AGENTS.md

## Project Rules

- Reply in Vietnamese when the user writes in Vietnamese.
- When editing Java Spring Boot code, preserve the existing style in the project.
- Always read the current code before suggesting or making changes.
- Do not refactor anything outside the scope of the current task.
- When adding documentation diagrams, use PlantUML under `docs/`.

## Query Optimization & N+1 Prevention

- Before writing queries involving entity relationships (OneToMany, ManyToMany),
  check whether `@EntityGraph`, `JOIN FETCH`, or batch fetch is already in place.
- Do not access lazy-loaded associations inside loops. If iteration over a collection
  is needed, fetch it upfront with a single query.
- Prefer `@EntityGraph` or JPQL `JOIN FETCH` over letting Hibernate generate
  multiple SELECT statements automatically.
- For large result sets (> 100 records), use `@BatchSize` or
  `spring.jpa.properties.hibernate.default_batch_fetch_size` instead of fetching
  records one by one.
- Never call a repository inside a loop (`for`, `stream().map()`, etc.).
  When looking up multiple IDs, use `findAllById()` or a single `IN (...)` query.
- Enable SQL logging in the dev environment to catch N+1 issues early:
  `spring.jpa.show-sql=true` + `logging.level.org.hibernate.SQL=DEBUG`.
- When adding a new relationship to an entity, always comment the reason for
  choosing `FetchType.LAZY` or `EAGER` directly on the annotation.
- When only a subset of fields is needed, use DTO projections
  (interface-based or `@Query` with a `new` constructor) instead of loading
  the full entity.