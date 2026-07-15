# yscord-web — Kotlin / Spring Boot + Angular

A full-stack YouTube music player served from a single Spring Boot jar. A Kotlin
backend proxies audio through **yt-dlp** and persists the player state in
**PostgreSQL** via **Exposed** (JetBrains' Kotlin SQL framework). An **Angular 21**
SPA with an NgRx SignalStore plays it in a control panel modelled on the yscord
Discord bot's message panel, and rehydrates its queue from the backend on load.

## Stack
- Kotlin 2.4 / JDK 21, Spring Boot 4.1 (Web, JDBC, Actuator, Validation)
- Exposed 1.0 (query builder + schema in code) over PostgreSQL 17
- A tiny Knex-style migration runner (up/down/rollback), no Flyway/Liquibase
- Angular 21 + `@ngrx/signals`, built into the jar via node-gradle
- yt-dlp (audio), Docker + docker-compose, GitHub Actions → GHCR

## Architecture

One Spring Boot jar serves both the API and the SPA — single origin, no CORS.

```
src/main/kotlin/de/yscord/player       yt-dlp proxy + persisted player state
src/main/kotlin/de/yscord/player/db    Exposed tables + migration runner
src/main/resources/static              ← `ng build` output, bundled into the jar
frontend/                              Angular workspace (SignalStore + panel UI)
```

- **Dev:** `ng serve` (port 4300) proxies `/api` to Spring (8080) via `proxy.conf.json`.
- **Prod:** `ng build` writes to `resources/static`; Spring serves the SPA at `/`.
- **`./gradlew build`** compiles the frontend (node-gradle downloads its own Node),
  runs backend tests on H2, and assembles a single runnable jar with the SPA inside.

## Run locally
```bash
# Everything in containers (app + Postgres):
docker compose up --build            # → http://localhost:8080

# OR: DB in Compose, app from the IDE/CLI:
docker compose up -d db
./gradlew bootRun

# Frontend hot-reload during development:
cd frontend && npm start             # → http://localhost:4300 (proxies /api to :8080)
```
Health: http://localhost:8080/actuator/health

## Endpoints
| Method | Path             | Purpose                                    |
|--------|------------------|--------------------------------------------|
| GET    | /api/resolve?q=… | Resolve a link or search → track metadata  |
| GET    | /api/stream/{id} | Audio bytes; HTTP Range (206) for seeking  |
| GET    | /api/player      | Load the persisted snapshot (queue + state)|
| PUT    | /api/player      | Replace the snapshot with the client's     |

`/api/stream/{id}` downloads best audio via yt-dlp into a temp cache once per id,
then serves the file with hand-rolled `206 Partial Content` so the panel can seek.

## Persistence & migrations (Exposed)

Tables are defined in Kotlin (`de.yscord.player.db.PlayerTables`), not annotations —
the Knex-style "schema as code" approach. The player snapshot (queue + index + loop
+ volume) is persisted on every change (debounced) and rehydrated on load.

Schema is managed by a small migration runner (`MigrationRunner`), a Knex analogue:

- Migrations are `Migration` beans with `up()` / `down()`, ordered by `version`.
- On startup, pending migrations are applied and recorded in `schema_migrations`.
- `java -jar app.jar --db.rollback=N` reverts the last N migrations (`down()`).

```bash
# roll back the last migration
./gradlew bootRun --args='--db.rollback=1'
```

### Database dumps
Dumps are a Postgres feature, handled by the db container:
```bash
docker compose exec db pg_dump -U yscord -d yscord > dump.sql          # dump
docker compose exec -T db psql -U yscord -d yscord < dump.sql          # restore
```

## Deploying the image
CI pushes `ghcr.io/senyaak/yscord-web` to GHCR. `docker-compose.yml` uses the
published image when present and falls back to a local build (`pull_policy: missing`);
`docker compose up --build` forces a rebuild.

## Next steps
- Progressive streaming (or a `/api/prepare` step) so long tracks start before the
  full download finishes — currently the first play blocks until yt-dlp is done.
- Testcontainers so tests exercise real Postgres + the migrations, not just H2.
- Per-user state (auth) instead of a single global player.
