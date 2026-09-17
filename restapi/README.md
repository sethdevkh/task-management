# restapi

Spring Boot **4.1.1** API for the Task Management MVP. Java **25**.

## Prerequisites

- JDK 25
- Maven Wrapper (`./mvnw`; no global Maven install required)

## Run locally

```bash
./mvnw test
./mvnw spring-boot:run
```

Health check (unauthenticated):

```bash
curl http://localhost:8080/api/health
```

Expected: `200` with `{"status":"UP"}`.

Every other `/api/**` path returns `401` until login is added in a later phase.

## Profiles

| Profile | When to use | H2 console | Notes |
| --- | --- | --- | --- |
| default (no `--spring.profiles.active`) | Local Hello World / tests | Disabled | In-memory H2. No console. |
| `local` | Developer machine when you want the H2 console | Enabled at `/h2-console` | Do not use in Dokploy. |
| `prod` | Deployed API | Forced off | Refuses to boot if `spring.h2.console.enabled=true`. Still in-memory H2 until Phase 4 (MySQL). |

Activate a profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or set `SPRING_PROFILES_ACTIVE`. Config comes from the environment in deploy; do not bake secrets into the image.

## Docker

Build and run (non-root user, port 8080):

```bash
docker build -t task-restapi .
docker run --rm -p 8080:8080 --name task-restapi task-restapi
curl http://localhost:8080/api/health
```

Production-like container:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  --name task-restapi task-restapi
```

Do not pass `SPRING_H2_CONSOLE_ENABLED=true` with `prod`.
