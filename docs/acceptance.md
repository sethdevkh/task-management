# MVP acceptance (Phase 9)

Maps to Success Metrics in [requirements.md](./requirements.md). This file records the walkthrough. It does not rewrite the PRD.

**Date:** 2026-09-17  
**Result:** **PASS** on a production-like stack with Spring Security enabled.  
**Live HTTPS Dokploy hosts:** not attached yet ([TODO.md](./TODO.md) item 4). Re-run the same script against `https://<web-host>/` after Hello World is live. Do not disable Security to make this pass.

## What was executed

Seeded demo users from [operators.md](./operators.md). Security filter chain on. JWT from `POST /api/auth/login`. H2 console off (test/default and `prod` profiles).

| Job | Result | Evidence |
| --- | --- | --- |
| Lead: who is overloaded? | PASS | Dashboard per-member `TO_DO` / `IN_PROGRESS`. `MvpWalkthroughTest.seededLeadCanAnswerOverloadAndMissingStandupsWithoutAnotherEndpoint` |
| Lead: who has not stood up today? | PASS | Dashboard standup presence (submitted vs missing). Same test. |
| Member: create a task and submit today’s standup in one sitting | PASS | My Tasks create → **Submit today’s standup** link → standup form. `MvpWalkthroughTest.seededMemberCanCreateTaskAndSubmitTodayStandupInOneSitting` |
| Freshness after status change / standup | PASS | Reload dashboard; totals match the database. No websocket. `MvpWalkthroughTest.dashboardTotalsMatchDatabaseAfterStatusChangeAndStandupSubmit` |
| RBAC | PASS | `RbacSuiteTest` — member JWT is **403** (never **200**) for another member’s task, another member’s standup, lead list/assign, lead standup board, and lead dashboard |

Production-like here means: the real Security config, real JWT login, seeded users, and CI (`./mvnw test`). It is **not** a locally disabled `SecurityFilterChain`.

## Lead walkthrough (Casey)

1. Open the web origin (`https://<web-host>/` when deployed; local Vite only for UI chrome).
2. Sign in as `casey@demo.local` (demo password from operators.md). Land on **Team dashboard**.
3. **Who is overloaded?** Read **Per-member workload**. The person with the highest To do + In progress is the overload answer. Do not leave the dashboard.
4. **Who has not stood up today?** Read **Standup presence**. Rows marked Missing are the answer. Date is today UTC.
5. Optional: **Refresh** (or reload the page) after a member changes status or submits a standup. Totals must change. There is no live push.

## Member walkthrough (Alex)

1. Sign out. Sign in as `alex@demo.local`. Land on **My Tasks**.
2. Create a task (title required). The list must show it.
3. Follow **Submit today’s standup** (or **Today standup** in the nav). Fill at least one of Done / Doing / Blockers. Save.
4. History must show today’s UTC row. Refresh the page; the task and standup are still there.

## HTTPS operator re-run (after item 4)

Do this on the deployed hosts, not on a Security-off local profile:

```bash
curl -fsS https://<api-host>/api/health
# {"status":"UP"}

curl -sS -o /dev/null -w "%{http_code}\n" https://<api-host>/api/tasks
# 401
```

Then repeat the lead and member scripts in the browser at `https://<web-host>/`. Record pass/fail in this file. CORS origin must be that HTTPS web origin.

## Security recap for this gate

- Secrets (`JWT_SECRET`, `MYSQL_*`, demo passwords, Dokploy webhooks) stay in Dokploy / local `.env`. None are in images or GitHub workflows.
- `prod` refuses H2 console, `ddl-auto=create` / `create-drop`, short/missing JWT secret, and CORS `*` / `http://`.
- `/api/health` is the only unauthenticated **GET**. `POST /api/auth/login` is the other public path. `ProdHardeningTest` fails CI if another `/api/**` GET is public.
- API responses send `X-Content-Type-Options`, `X-Frame-Options: DENY` (H2 off), `Referrer-Policy`, `Permissions-Policy`. HTTPS requests send HSTS. The web image sends the same browser headers from nginx. TLS stays at the Dokploy edge.
