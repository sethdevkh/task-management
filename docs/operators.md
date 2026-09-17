# Operators

How to run and seed the Task Management MVP. This is **not** product documentation. Demo accounts are for local and demo environments only.

## Schema strategy

| Environment | Database | How schema is applied | Hibernate `ddl-auto` |
| --- | --- | --- | --- |
| Tests | H2 in-memory (`MODE=MySQL`) | Flyway `V1__init.sql` | `validate` |
| Local (`local` profile) | MySQL 8.4 via `restapi/compose.yaml` | Flyway | `validate` |
| Deployed (`prod` profile) | MySQL | Flyway | `none` (boot fails on `create` / `create-drop`) |

JPA DDL is **not** used to create tables in local or prod. The Flyway script in [`restapi/src/main/resources/db/migration`](../restapi/src/main/resources/db/migration) is the source of truth.

## Entities and tenancy

One team per user. MVP has a single seeded team.

| Entity | Purpose | Tenancy |
| --- | --- | --- |
| `Team` | The one team a lead owns | Root |
| `User` | Login identity, role (`TEAM_LEAD` or `TEAM_MEMBER`), password hash | `team_id` (required) |
| `Task` | Work item | `team_id` copied from the **creator** |
| `Standup` | One Done/Doing/Blockers row per user per **UTC** calendar date | `team_id` copied from the **author**; unique `(user_id, standup_date)` |

`team_id` is **never a client-supplied override**. Upcoming APIs must take the team from the authenticated user (the persisted `User.team`). Request bodies must not include `team_id`. Repository methods used by those APIs must filter by that server-side team id so a query cannot return another team’s tasks or standups.

Do not add `findAll()`-based product endpoints for tasks or standups. Use `findByTeamId` / `findByIdAndTeamId` (and the standup date variants).

There is still **no** task or standup HTTP API. Login is `POST /api/auth/login`; see [api.md](./api.md).

## Local MySQL

From `restapi/`:

1. Copy `.env.example` to `.env`.
2. Set MySQL and demo password values (below). `.env` is gitignored.
3. `docker compose up -d`
4. `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`

See [`restapi/README.md`](../restapi/README.md) for profile names and Docker.

## Demo credentials (demo-only)

These accounts exist only to walk the MVP. They are **not** production identities. Do not reuse them outside local/demo. Do not put these passwords in Docker images, Compose defaults, or GitHub Actions.

| Role | Email (login id) | Display name | Password env var | Demo password |
| --- | --- | --- | --- | --- |
| `TEAM_LEAD` | `casey@demo.local` | Casey Lead | `DEMO_LEAD_PASSWORD` | `demo-lead-change-me` |
| `TEAM_MEMBER` | `alex@demo.local` | Alex Member | `DEMO_MEMBER_ALEX_PASSWORD` | `demo-alex-change-me` |
| `TEAM_MEMBER` | `bailey@demo.local` | Bailey Member | `DEMO_MEMBER_BAILEY_PASSWORD` | `demo-bailey-change-me` |

All three belong to team **Platform**. Passwords are hashed with BCrypt (`BCryptPasswordEncoder`) before insert. Seed runs only when the `users` table is empty.

Suggested local `.env` (MySQL names are not secret; **passwords are** — use the demo values only on a developer machine):

```bash
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=taskmgmt
MYSQL_USER=taskapp
MYSQL_PASSWORD=local-mysql-change-me
MYSQL_ROOT_PASSWORD=local-root-change-me

DEMO_LEAD_PASSWORD=demo-lead-change-me
DEMO_MEMBER_ALEX_PASSWORD=demo-alex-change-me
DEMO_MEMBER_BAILEY_PASSWORD=demo-bailey-change-me

JWT_SECRET=replace-with-openssl-rand-base64-48-output
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

On first local boot with `app.seed.enabled=true` (the `local` profile), the seeder creates the team and three users. Later boots skip seed if any user row exists.

## Rotate demo passwords

Seed does **not** overwrite existing hashes on every boot (so a forgotten `APP_SEED_ENABLED=true` in prod cannot silently reset people after you already changed them).

**Local, empty database is fine:**

1. Change `DEMO_*` in `.env`.
2. Recreate the volume so seed runs again:

```bash
docker compose down -v
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Keep the database, change hashes:**

1. Generate a BCrypt hash (cost 10, Spring’s default), for example from `jshell` with `BCryptPasswordEncoder`.
2. Update the row:

```sql
UPDATE users
SET password_hash = '$2a$...'
WHERE email = 'casey@demo.local';
```

3. Put the new plaintext only in this file (or your password manager) and in the environment of the process that needs to seed a **fresh** database. Do not commit the plaintext anywhere else.

**Deployed demo:** set `APP_SEED_ENABLED=true` only for the first boot of an empty database, then set it back to `false`. After that, rotate with SQL as above.

## JWT signing key

`JWT_SECRET` must live in Dokploy / local `.env` only. Do not commit a signing key, put one in the image, or add one to a workflow file. HS256 needs at least 32 bytes:

```bash
openssl rand -base64 48
```

Local `.env` also needs `CORS_ALLOWED_ORIGINS=http://localhost:5173` (Vite). Prod must be the HTTPS web origin; see [deploy.md](./deploy.md) and [api.md](./api.md).

## Deployed MySQL (`task-api`)

Add these to the Dokploy env for `task-api` (see also [deploy.md](./deploy.md)):

| Variable | Notes |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `MYSQL_HOST` | Companion MySQL hostname |
| `MYSQL_PORT` | Usually `3306` |
| `MYSQL_DATABASE` | Database name |
| `MYSQL_USER` | App user |
| `MYSQL_PASSWORD` | App password |
| `APP_SEED_ENABLED` | `true` only to seed an empty demo DB, then `false` |
| `DEMO_*_PASSWORD` | Required only while seeding |
| `JWT_SECRET` | Required. ≥ 32 bytes |
| `CORS_ALLOWED_ORIGINS` | `https://<web-host>` |

Do not set `SPRING_H2_CONSOLE_ENABLED=true`. Do not set `SPRING_JPA_HIBERNATE_DDL_AUTO` to `create` or `create-drop`.
