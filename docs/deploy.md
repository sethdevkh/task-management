# Deploy (Hello World)

GCP + **Dokploy**. Two services, not one container. This document is the operator runbook for the Phase 1 Hello World.

Hello World is live when:

- `GET https://<api-host>/api/health` returns `200` JSON `{"status":"UP"}`
- `https://<web-host>/` serves the Task Management Hello World page over HTTPS

Login and CORS are configured in Phase 5. MySQL is configured in Phase 4; see [operators.md](./operators.md).

## Service names

Use these names in Dokploy so later docs and CI stay consistent.

| Dokploy service | Repo path | Dockerfile | Container port | Public path |
| --- | --- | --- | --- | --- |
| `task-api` | `restapi/` | `restapi/Dockerfile` | `8080` | `/api/health` |
| `task-web` | `web/` | `web/Dockerfile` | `8080` | `/` |

If the git root is the monorepo (`task-management`), set each service's **build context** (or "base directory") to `restapi` and `web` respectively. Do not build from the repo root; each Dockerfile expects its own directory as context.

## Create the two Dokploy services

1. Point Dokploy at the git remote that contains this monorepo.
2. Create application **`task-api`**:
   - Build type: Dockerfile
   - Dockerfile: `Dockerfile` (inside the `restapi` context)
   - Published port: `8080`
   - Health check: HTTP `GET /api/health` on port `8080`. Expect `200`.
   - Domain: the API hostname (example: `api.example.com`)
3. Create application **`task-web`**:
   - Build type: Dockerfile
   - Dockerfile: `Dockerfile` (inside the `web` context)
   - Published port: `8080`
   - Health check: HTTP `GET /` on port `8080`. Expect `200`.
   - Domain: the web hostname (example: `app.example.com`)

Do not put the API and the web UI in the same service.

## Environment (config only, never in the image)

Images contain no secrets. Set these in the Dokploy UI for **`task-api`**:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` (optional; already the default) |
| `MYSQL_HOST` | Companion MySQL hostname |
| `MYSQL_PORT` | `3306` unless the companion uses another port |
| `MYSQL_DATABASE` | Database name |
| `MYSQL_USER` | App user (env only) |
| `MYSQL_PASSWORD` | App password (env only) |
| `JWT_SECRET` | HS256 signing key, **≥ 32 bytes**. Generate with `openssl rand -base64 48`. Never reuse the test secret. |
| `CORS_ALLOWED_ORIGINS` | Exact HTTPS origin of `task-web`, e.g. `https://app.example.com`. No `*`. No `http://`. |

Do **not** set `SPRING_H2_CONSOLE_ENABLED=true`. The `prod` profile disables the console and refuses to boot if it is enabled. Do not set `SPRING_JPA_HIBERNATE_DDL_AUTO` to `create` or `create-drop`. Prod also refuses to boot if `JWT_SECRET` is missing/short or if CORS is `*` / HTTP.

`task-web` needs the API origin **at image build time** (Vite inlines it):

| Build arg | Value |
| --- | --- |
| `VITE_API_BASE_URL` | `https://<api-host>` (no trailing slash) |
| `VITE_AUTH_MODE` | `live` (the Dockerfile rejects anything else) |

Do not put JWT secrets or demo passwords in the web image. The browser sends `Authorization: Bearer <jwt>` after login; it does not use cookies, so CORS credentials stay off.

JWT signing keys belong in Dokploy env for `task-api` only. Demo seed passwords, if you seed an empty demo database, are documented in [operators.md](./operators.md) and must not go in the image or in GitHub workflows.

## HTTPS and edge

- Attach each domain in Dokploy and enable HTTPS (Let's Encrypt / the Dokploy certificate flow). Do not publish the API or web over plain HTTP on the public internet.
- The container listens on HTTP `8080`. TLS terminates at the Dokploy / Traefik edge.
- Prod CORS origins must be `https://<web-host>`. The API will not boot in `prod` with `*` or `http://`.
- Do not publish the H2 console. It is off in `prod` and not routed.

## Health URL

After DNS and TLS:

```text
https://<api-host>/api/health
```

Example check:

```bash
curl -fsS https://<api-host>/api/health
```

Expected body: `{"status":"UP"}`.

The web Hello World is:

```text
https://<web-host>/
```

Replace `<api-host>` and `<web-host>` with the hostnames you attached in Dokploy.

## Restrict SSH / admin

On the GCP VM that runs Dokploy:

- Disable password SSH. Keys only.
- Restrict SSH (`tcp/22`) to operator IPs. Do not leave it open to `0.0.0.0/0`.
- Keep the Dokploy UI behind HTTPS and a strong admin password (or SSO if you add it later). Do not expose Docker / Dokploy admin ports beyond what Dokploy itself needs.
- Do not bind the API or web containers to a public host port if the edge proxy already fronts them.

## Roll back

Dokploy keeps previous successful deployments per service.

1. Open the service (`task-api` or `task-web`).
2. Open **Deployments**.
3. Redeploy the last known-good deployment (the previous image/commit that passed the health check).

Roll back **one service at a time**. Hello World has no shared schema yet, so API and web are independent.

If a deploy is unhealthy (API health check failing, or web not serving `/`):

1. Do not keep retrying a bad commit.
2. Roll back that service to the previous deployment.
3. Confirm `https://<api-host>/api/health` and `https://<web-host>/` again.

Git-level rollback: revert the commit on the deployed branch and let Dokploy rebuild. Prefer the Dokploy previous-deployment action when you need the site up immediately.

## Auto-deploy from git

Do **not** enable Dokploy’s GitHub Auto Deploy on push. That path ignores GitHub Actions.

After Hello World services exist, CI on `main` triggers each service’s deploy webhook. Webhook URLs live in GitHub Actions secrets, not in this repo. See [ci.md](./ci.md).

## What this increment does not deploy

- Dashboard aggregates API (the dashboard screen still uses in-memory mock data after live login)

MySQL for `task-api` is required. Schema is Flyway, not Hibernate `create`. Details: [operators.md](./operators.md). Login, task, and standup contracts: [api.md](./api.md).
