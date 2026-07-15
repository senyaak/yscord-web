# todo — Kotlin / Spring Boot

Kleine CRUD-Todo-API. Gebaut, um den dmTECH-Stack (Kotlin + Spring Boot,
PostgreSQL, Docker, CI/CD) auszuprobieren und im Interview vergleichen zu können.

## Stack
- Kotlin 2.4 / JDK 21
- Spring Boot 4.1 — Web (MVC), Data JPA, Validation, Actuator
- PostgreSQL (Prod), H2 in-memory (Tests)
- Gradle (Wrapper), Docker + docker-compose
- GitHub Actions → GHCR

## Lokal starten
```bash
# 1. Nur die DB per Compose, App aus der IDE/CLI:
docker compose up -d db
./gradlew bootRun

# ODER: alles in Containern
docker compose up --build
```
App: http://localhost:8080 · Health: http://localhost:8080/actuator/health

## Endpoints
| Methode | Pfad             | Zweck            |
|---------|------------------|------------------|
| GET     | /api/todos       | Liste            |
| GET     | /api/todos/{id}  | Einzeln          |
| POST    | /api/todos       | Anlegen (201)    |
| PUT     | /api/todos/{id}  | Ersetzen         |
| DELETE  | /api/todos/{id}  | Löschen (204)    |

```bash
curl -s localhost:8080/api/todos
curl -s -XPOST localhost:8080/api/todos -H 'Content-Type: application/json' \
     -d '{"title":"Interview vorbereiten","description":"Spring-Basics"}'
```

## Schichten (und die NestJS-Brücke fürs Gespräch)
| Hier (Spring)                    | NestJS-Äquivalent                    |
|----------------------------------|--------------------------------------|
| `@RestController`                | `@Controller`                        |
| `@Service` + Konstruktor-DI      | `@Injectable()` + Constructor-DI     |
| `JpaRepository`                  | `Repository<T>` (TypeORM)            |
| `@Entity`                        | `@Entity()` (TypeORM)                |
| DTO + `@field:NotBlank`+`@Valid` | DTO + `class-validator` + ValidationPipe |
| `@RestControllerAdvice`          | globaler `ExceptionFilter`           |
| `@Transactional` + Dirty-Checking| manuelles `repo.save()` / QueryRunner|

**Kernunterschied fürs Gespräch:** In Nest schreibe ich `save()` explizit; in JPA
reicht es, die im `@Transactional`-Kontext geladene Entity zu mutieren — Hibernate
flusht die Änderung per Dirty-Checking beim Commit. Konfiguration statt Code:
Spring Data leitet die Repository-Implementierung aus dem Interface ab.

## Nächste Schritte (falls gefragt)
- Flyway-Migrationen statt `ddl-auto=update` (Prod: `validate`)
- Testcontainers für echtes Postgres im Test statt H2
- Pagination auf `GET /api/todos`
