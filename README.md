# yscord-web — Kotlin / Spring Boot + Angular

A full-stack YouTube music player served from a single Spring Boot jar: a Kotlin
backend proxies audio through **yt-dlp**, and an **Angular 21** SPA (with an NgRx
SignalStore) plays it in a control panel modelled on the yscord Discord bot's
message panel. A small **todo CRUD** module rides along as a plain REST demo.

Built to exercise the dmTECH stack (Kotlin + Spring Boot, PostgreSQL, Docker,
CI/CD) end to end, frontend included.

## Stack
- Kotlin 2.4 / JDK 21
- Spring Boot 4.1 — Web (MVC), Data JPA, Validation, Actuator
- Angular 21 + `@ngrx/signals` (SignalStore), built into the jar via node-gradle
- yt-dlp (audio proxy), PostgreSQL (prod), H2 in-memory (tests)
- Gradle (wrapper), Docker + docker-compose, GitHub Actions → GHCR

## Architecture

One Spring Boot jar serves both the API and the SPA — single origin, no CORS.

```
src/main/kotlin/de/yscord/player   yt-dlp proxy: /api/resolve, /api/stream/{id}
src/main/kotlin/de/senya/todo      todo CRUD REST module
src/main/resources/static          ← `ng build` output, bundled into the jar
frontend/                          Angular workspace (SignalStore + panel UI)
```

- **Dev:** `ng serve` (port 4300) proxies `/api` to Spring (8080) via `proxy.conf.json`.
- **Prod:** `ng build` writes to `resources/static`; Spring serves the SPA at `/`.
- **`./gradlew build`** compiles the frontend (node-gradle downloads its own Node),
  runs backend tests, and assembles a single runnable jar with the SPA inside.

## Run locally
```bash
# Everything in containers (app + Postgres):
docker compose up --build
# → http://localhost:8080

# OR: DB in Compose, app from the IDE/CLI:
docker compose up -d db
./gradlew bootRun

# Frontend hot-reload during development:
cd frontend && npm start        # → http://localhost:4300 (proxies /api to :8080)
```
Health: http://localhost:8080/actuator/health

## Endpoints

### Player
| Method | Path                | Purpose                                   |
|--------|---------------------|-------------------------------------------|
| GET    | /api/resolve?q=…    | Resolve a link or search → track metadata |
| GET    | /api/stream/{id}    | Audio bytes; HTTP Range (206) for seeking |

The stream endpoint downloads best audio via yt-dlp into a temp cache once per id,
then serves the file with hand-rolled `206 Partial Content` so the progress bar
can seek and replays are instant.

### Todo (CRUD demo)
| Method | Path             | Purpose        |
|--------|------------------|----------------|
| GET    | /api/todos       | List           |
| GET    | /api/todos/{id}  | One            |
| POST   | /api/todos       | Create (201)   |
| PUT    | /api/todos/{id}  | Replace        |
| DELETE | /api/todos/{id}  | Delete (204)   |

## Layers (and the NestJS bridge for the interview)
| Here (Spring)                     | NestJS equivalent                        |
|-----------------------------------|------------------------------------------|
| `@RestController`                 | `@Controller`                            |
| `@Service` + constructor DI       | `@Injectable()` + constructor DI         |
| `JpaRepository`                   | `Repository<T>` (TypeORM)                |
| `@Entity`                         | `@Entity()` (TypeORM)                     |
| DTO + `@field:NotBlank` + `@Valid`| DTO + `class-validator` + ValidationPipe |
| `@RestControllerAdvice`           | global `ExceptionFilter`                 |
| `@Transactional` + dirty checking | manual `repo.save()` / QueryRunner       |

**Key difference for the interview:** in Nest I call `save()` explicitly; in JPA it
is enough to mutate an entity loaded inside a `@Transactional` context — Hibernate
flushes the change via dirty checking on commit. Configuration over code: Spring
Data derives the repository implementation from the interface.

## Next steps (if asked)
- Progressive streaming (or a `/api/prepare` step) so long tracks start before the
  full download finishes — currently the first play blocks until yt-dlp is done.
- Flyway migrations instead of `ddl-auto=update` (prod: `validate`).
- Testcontainers for real Postgres in tests instead of H2.
- Pagination on `GET /api/todos`.
