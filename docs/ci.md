# CI and auto-deploy

GitHub Actions is the gate. Dokploy is the runtime. A push to `main` must not ship unless **green CI** has already passed.

## Green CI

**Green CI** means both of these jobs succeeded on the same commit:

| Job | Workflow | What it runs | Fails the workflow when |
| --- | --- | --- | --- |
| `restapi` | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | `./mvnw -B test` in `restapi/` (Java 25) | Compile error or any test failure |
| `web` | same file | `npm ci`, `npm run lint`, `npm run build` in `web/` (Node 24) | Lint error or build/typecheck failure |

There is no JWT secret, database password, or frontend env in these jobs. They do not print `env` dumps.

The `deploy` job is **not** part of green CI. It only runs after both jobs succeed, and only on `main`.

## What runs on a pull request

On `pull_request`:

1. `restapi` compiles and tests.
2. `web` lints and builds.
3. `deploy` is skipped.

Require both `restapi` and `web` as branch-protection checks on `main` so a red PR cannot merge.

## What runs on `main`

On `push` to `main`:

1. The same `restapi` and `web` jobs run again on the merged commit.
2. If both succeed, `deploy` POSTs the per-service Dokploy webhooks (when the secrets exist).

Concurrency: in-progress runs on a PR branch are cancelled when a newer commit is pushed. Runs on `main` are not cancelled.

## Dokploy auto-deploy (after CI)

Dokploy’s built-in GitHub **Auto Deploy** fires on push and **does not wait** for GitHub Actions. Using it would deploy a red `main`. Leave Auto Deploy **off** for `task-api` and `task-web`.

Instead, CI triggers Dokploy only after green CI on `main`:

1. In Dokploy, open each application’s deploy webhook URL (`task-api`, `task-web`). These URLs are deploy tokens.
2. In the GitHub repo: **Settings → Secrets and variables → Actions**, add:
   - `DOKPLOY_WEBHOOK_TASK_API`
   - `DOKPLOY_WEBHOOK_TASK_WEB`
3. Do not put those URLs in the repo, in workflow YAML, or in logs on purpose. Do not use a full Dokploy API token here; each webhook should only deploy that one service.

If a webhook secret is missing, that service is skipped and the job still succeeds (so Hello World CI is not blocked before Dokploy exists). Once a secret is set, a non-2xx webhook response fails `deploy`.

`deploy` does not receive JWT or database secrets. Those stay in Dokploy environment config ([deploy.md](./deploy.md)). API tests use in-memory H2; they do not need `MYSQL_*` or demo passwords in the workflow.

## Action pins

Third-party actions are pinned to full commit SHAs with a version comment so Dependabot can still open update PRs. See [`.github/dependabot.yml`](../.github/dependabot.yml) (`github-actions`, weekly).

`GITHUB_TOKEN` is limited with workflow `permissions: contents: read`. Checkout does not persist credentials.

The `restapi` job includes `TaskApiTest`, `StandupApiTest`, and `DashboardApiTest`: member JWT (not a missing-token 401) is **403** for another member’s task or standup by id, lead list/assign, lead standup board, lead dashboard aggregates, illegal deletes, and past-day standup edits. Blank title, blank standup, and off-team assignee are **400**.

## Operator checklist

- [ ] Branch protection on `main`: require `restapi` and `web`
- [ ] Dokploy Auto Deploy **off** for `task-api` and `task-web`
- [ ] GitHub secrets `DOKPLOY_WEBHOOK_TASK_API` and `DOKPLOY_WEBHOOK_TASK_WEB` set
- [ ] After merge to `main`, confirm Dokploy started a deployment for each service
