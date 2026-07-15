# HANDOFF — состояние проекта (переход из сессии yscord)

> Файл-передача контекста. Проект собран в рамках подготовки к интервью **dmTECH 22.07, 09:30**
> (Fullstack: Angular + **Kotlin/Spring Boot**). Заметка о вакансии: `~/obsidian/Bewerbungen/dmTECH.md`.

## TL;DR
Рабочий **Kotlin + Spring Boot 4.1** CRUD-Todo с Postgres, Docker и GitHub Actions CI/CD.
Сборка и тесты зелёные. Не хватает только живого прогона через docker compose (демон был не запущен).

## Стек (версии достоверны из Maven Central — start.spring.io на момент сборки лежал)
- JDK 21 (`/usr/lib/jvm/java-21-openjdk`, `java` уже в PATH), Kotlin 2.4.10
- Spring Boot 4.1.0 — Web, Data JPA, Validation, Actuator
- Gradle 8.14.3 (wrapper закоммичен), PostgreSQL 17 / H2 (тесты)

## Что готово ✅
- Домен `de.senya.todo.todo`: Entity → Repository → Service → Controller, полный CRUD на `/api/todos`
- DTO + валидация, глобальный `@RestControllerAdvice` c `ProblemDetail`
- `application.yml` (Postgres через env), тест-профиль на H2
- `Dockerfile` (multi-stage, non-root), `docker-compose.yml` (app + postgres, healthchecks)
- `.github/workflows/ci.yml` — build/test → образ → push в GHCR
- `README.md` с таблицей-мостом Spring↔NestJS (тезисы для интервью)
- `./gradlew build` → BUILD SUCCESSFUL, 4/4 интеграционных теста (Spring context + H2) зелёные

## Осталось ⏳ (следующий шаг)
Прогнать реальный Postgres-путь. Блокировало: docker-демон не запущен, группа `docker` пустая.

1. Включить docker (если ещё не сделано):
   ```fish
   sudo systemctl enable --now docker
   sudo usermod -aG docker $USER
   newgrp docker          # или перезапустить сессию, чтобы группа применилась
   ```
2. Поднять и проверить:
   ```fish
   docker compose up --build           # app + postgres
   # в другом окне:
   curl -s localhost:8080/api/todos
   curl -s -XPOST localhost:8080/api/todos -H 'Content-Type: application/json' \
        -d '{"title":"Interview vorbereiten"}'
   curl -s localhost:8080/actuator/health
   ```

## Быстрый старт для разработки
```fish
docker compose up -d db      # только БД
./gradlew bootRun            # приложение локально
# ./gradlew build            # сборка + тесты
```

## Идеи на потом (сильные пункты «я потрогал ваш стек» для 22.07)
- Flyway-миграции вместо `ddl-auto=update` (prod → `validate`)
- Testcontainers — гонять тесты на настоящем Postgres вместо H2
- Pagination на `GET /api/todos`

## Заметки по окружению
- Manjaro/Arch. `sudo` в агент-сессии недоступен (нет tty) — системные команды выполняет пользователь.
- start.spring.io на 15.07 отдавал 500 (не резолвил BOM Spring Boot 4.x) — каркас собран руками.
