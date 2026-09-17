# restapi

Spring Boot **4.1.1** API for the Task Management MVP. Java **25**.

## Prerequisites

- JDK 25
- Maven Wrapper (`./mvnw`; no global Maven install required)
- Docker (for local MySQL)

## Run locally

Local development uses **MySQL** (Compose) and the `local` profile. Tests use in-memory **H2**.

1. Copy [`.env.example`](./.env.example) to `.env` and fill values from [`docs/operators.md`](../docs/operators.md). Compose reads `.env` automatically. Do not commit `.env`.
2. Start MySQL:

```bash
docker compose up -d
```

3. Export the same variables (or rely on your shell) and run the API:

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Health check (unauthenticated):

```bash
curl http://localhost:8080/api/health
```

Expected: `200` with `{"status":"UP"}`.

Login (seeded users; see [`docs/operators.md`](../docs/operators.md) and [`docs/api.md`](../docs/api.md)):

```bash
curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"casey@demo.local","password":"'"${DEMO_LEAD_PASSWORD}"'"}'
```

Every other `/api/**` path returns `401` without a valid `Authorization: Bearer` token. Task endpoints are documented in [`docs/api.md`](../docs/api.md). There are no standup endpoints yet.

Tests (H2, no Compose required):

```bash
./mvnw test
```

## Profiles

| Profile | Database | H2 console | Schema | Seed |
| --- | --- | --- | --- | --- |
| (tests, no profile in CI) | In-memory H2 | Disabled | Flyway, then Hibernate `validate` | Test passwords only |
| `local` | MySQL via Compose | Enabled at `/h2-console` (generic JDBC UI; data lives in MySQL) | Flyway, then Hibernate `validate` | Demo users if the database is empty |
| `prod` | MySQL (deployed) | Forced off | Flyway, Hibernate `ddl-auto=none`. Refuses `create` / `create-drop` | Off unless `APP_SEED_ENABLED=true` |

Activate local:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or set `SPRING_PROFILES_ACTIVE`. Database credentials, demo seed passwords, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` come from the environment. Do not bake them into the image or into GitHub workflows.

Do not run the API without `local` or `prod`. Unprofiled `spring-boot:run` is not a supported developer path.

## Docker

Build and run (non-root user, port 8080). The image still needs a MySQL instance and env:

```bash
docker build -t task-restapi .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e MYSQL_HOST=host.docker.internal \
  -e MYSQL_PORT=3306 \
  -e MYSQL_DATABASE \
  -e MYSQL_USER \
  -e MYSQL_PASSWORD \
  -e DEMO_LEAD_PASSWORD \
  -e DEMO_MEMBER_ALEX_PASSWORD \
  -e DEMO_MEMBER_BAILEY_PASSWORD \
  -e JWT_SECRET \
  -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  --name task-restapi task-restapi
curl http://localhost:8080/api/health
```

Production-like container:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MYSQL_HOST \
  -e MYSQL_PORT=3306 \
  -e MYSQL_DATABASE \
  -e MYSQL_USER \
  -e MYSQL_PASSWORD \
  -e JWT_SECRET \
  -e CORS_ALLOWED_ORIGINS=https://app.example.com \
  --name task-restapi task-restapi
```

Do not pass `SPRING_H2_CONSOLE_ENABLED=true` with `prod`. Do not pass demo passwords unless you intend to seed an empty demo database (`APP_SEED_ENABLED=true`).
