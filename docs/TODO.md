# MVP implementation TODO

Source of truth: [requirements.md](./requirements.md).

This is a backlog of **small increments** for AI-assisted development. `restapi` (Spring Boot 4.1.1 / Java 25) and `web` (Vite + React + TypeScript) already exist as scaffolds. Do **not** recreate them from scratch.

CI/CD assumption: GitHub Actions for CI (lint / test / build) + Dokploy on GCP for deploy. Hello World is deployable **before** mocked UI and **before** JWT.

## Ground rules

- Work in `restapi` and `web` as separate services.
- One increment at a time.
- Mark an increment done only when its **Implement**, **Security**, and **Docs** boxes are all checked.
- RBAC is not a later epic. Every feature increment includes 403/401 tests for that surface.
- Do not implement items in [Do not do](#do-not-do).

---



## Phase 1 — Project init and Hello World

First deployable increment. After step 4, API health and the web hello page are live on Dokploy.

- [x] **1. API Hello World**
  - [x] Implement: confirm `restapi` boots (`./mvnw test` and run). Add `GET /api/health` that returns 200 JSON.
  - [x] Security: Spring Security is already on the classpath. Permit **only** `/api/health` (login comes later). Do not disable Security globally. Do not expose the H2 console in any non-local profile.
  - [x] Docs: write `restapi/README.md` (how to run, Java 25, profiles).

- [x] **2. Web Hello World**
  - [x] Implement: confirm `web` boots. Replace the Vite demo with a static Hello World page that names the product.
  - [x] Security: no secrets in frontend env yet. Do not call a private API until CORS is planned (Phase 5).
  - [x] Docs: write `web/README.md` (`npm run dev` / `build`).

- [x] **3. Dockerize both apps**
  - [x] Implement: Dockerfile for the API; Dockerfile for the web (static nginx or equivalent). Both run as containers locally.
  - [x] Security: non-root container user. No secrets baked into images. `.dockerignore` excludes `.env`, `target`, `node_modules`.
  - [x] Docs: container build and run commands in each README.

- [ ] **4. Deploy Hello World to Dokploy on GCP**
  - [ ] Implement: API and web as two Dokploy services. Health check hits `/api/health`. Web serves the hello page.
  - [x] Security: HTTPS at the edge. No public H2. Config via environment only. Restrict SSH/admin.
  - [x] Docs: write `docs/deploy.md` (service names, health URL, how to roll back).

---



## Phase 2 — CI/CD early

- [x] **5. GitHub Actions for** `restapi`
  - [x] Implement: on PR and main, compile and run tests. Fail the job on test failure.
  - [x] Security: no JWT/DB secrets in the workflow. Pin actions (SHA or Dependabot-tracked versions). Least-privilege `GITHUB_TOKEN`.
  - [x] Docs: start `docs/ci.md` with the API workflow (what runs on PR vs main).

- [x] **6. GitHub Actions for** `web`
  - [x] Implement: on PR and main, `lint` and `build`. Fail the job on either failure.
  - [x] Security: same as step 5. No frontend secrets in the workflow. Do not print env dumps.
  - [x] Docs: add the web workflow to `docs/ci.md`.

- [x] **7. Dokploy auto-deploy from git**
  - [x] Implement: deploy from the git remote after CI (or on `main` after green CI if Dokploy can gate on CI).
  - [x] Security: least-privilege deploy tokens. No JWT/DB secrets in workflow files. Tokens live in GitHub/Dokploy secrets, not the repo.
  - [x] Docs: finish `docs/ci.md` (how deploy is triggered, what “green CI” means).

---



## Phase 3 — Basic UI with mocked data

No real API. Screens exist so later increments only swap mocks for live calls.

Mock role is **not** authorization. UI hiding is insufficient. Do not send mock tokens to a real API.

- [x] **8. Routing and Shadcn/ui shell**
  - [x] Implement: add routing and a Shadcn/ui shell (layout, nav placeholder).
  - [x] Security: no auth bypass via client routes. Shell must not assume a role until mock login (step 9).
  - [x] Docs: start `docs/frontend.md` (routes, shell).

- [x] **9. Mock login**
  - [x] Implement: login screen that sets a **client-only** role (`TEAM_MEMBER` | `TEAM_LEAD`). Label the screen as mock.
  - [x] Security: do not persist passwords. Do not mint JWTs on the client. Mock role must not be sent to a real API.
  - [x] Docs: document mock vs live flag in `docs/frontend.md`.

- [x] **10. Member My Tasks (mock)**
  - [x] Implement: list / create / change status with in-memory mock tasks.
  - [x] Security: only render this surface for mock `TEAM_MEMBER` (and lead if they use it). Still not real RBAC.
  - [x] Docs: describe the My Tasks screen in `docs/frontend.md`.

- [x] **11. Member today standup (mock)**
  - [x] Implement: Done / Doing / Blockers form with mock upsert (one entry per day).
  - [x] Security: do not treat a blank submit as success if all three fields are empty (match PRD: at least one required).
  - [x] Docs: describe the standup form in `docs/frontend.md`.

- [x] **12. Lead dashboard (mock)**
  - [x] Implement: workload counts, team status mix, 7-day completion placeholder, standup presence.
  - [x] Security: hide this route when mock role is `TEAM_MEMBER`. Document that hiding is not authorization.
  - [x] Docs: describe dashboard widgets in `docs/frontend.md`.

- [x] **13. Lead task list and assign (mock)**
  - [x] Implement: team task list + assign UI against mock data. Hide lead routes when mock role is member.
  - [x] Security: member mock role cannot open lead task/assign screens. Still client-only.
  - [x] Docs: finish mock-screen map in `docs/frontend.md`.

---



## Phase 4 — Data foundation

API schema and seed only. No product endpoints yet beyond health.

- [x] **14. Database profiles**
  - [x] Implement: MySQL for local (Compose) and deployed; H2 for tests. `application-local` vs `application-prod` profiles.
  - [x] Security: DB credentials from env only. H2 console off except local. Prod profile must not use `ddl-auto=create`/`create-drop`.
  - [x] Docs: local Compose and profile names in `restapi/README.md`.

- [x] **15. Entities**
  - [x] Implement: `User`, `Team`, `Task`, `Standup`. `team_id` on task and standup. Unique `(user_id, standup_date)` (UTC date).
  - [x] Security: `team_id` is never a client-supplied override. Relations must not leak cross-team rows.
  - [x] Docs: write `docs/operators.md` (entity/tenancy notes). Schema strategy: Flyway/Liquibase or equivalent for prod; JPA DDL only for local if used.

- [x] **16. Seed data**
  - [x] Implement: one lead, two members, same team. Hashed passwords. Demo credentials documented as **demo-only**.
  - [x] Security: BCrypt (or Spring’s password encoder). JWT signing key **not** committed. Seed passwords only in operator docs, never in images or workflows.
  - [x] Docs: seed users, how to rotate demo passwords, and “`team_id` never client-supplied” in `docs/operators.md`.

---



## Phase 5 — Real auth

Replace mock login. Still no task/standup APIs.

- [x] **17. Login endpoint**
  - [x] Implement: `POST /api/auth/login` against seeded users only. Generic 401. Return JWT + role + display name. **No register.**
  - [x] Security: generic error (no user enumeration beyond the demo). No `team_id` in the login body. Passwords verified with the password encoder.
  - [x] Docs: start `docs/api.md` with the login contract.

- [x] **18. JWT filter**
  - [x] Implement: 401 on missing/expired token for all `/api/**` except `/api/health` and `/api/auth/login`.
  - [x] Security: short TTL. Signing key from env. Role taken from the token/user record, not the client body.
  - [x] Docs: public vs protected paths in `docs/api.md`.

- [x] **19. Web live login**
  - [x] Implement: login form calls the API; store token; 401 → login; `TEAM_MEMBER` → My Tasks; `TEAM_LEAD` → dashboard.
  - [x] Security: document bearer-in-`Authorization` vs httpOnly cookie (MVP may use Authorization; note XSS). Do not keep mock-role login as a prod path.
  - [x] Docs: live auth flow in `docs/frontend.md` and `docs/api.md`.

- [x] **20. CORS**
  - [x] Implement: allow only the web origin.
  - [x] Security: no `*` origin in prod. Credentials/header policy matches how the token is sent. HTTPS-only in prod.
  - [x] Docs: allowed origins in `docs/api.md` / `docs/deploy.md`.

---



## Phase 6 — Tasks

One verb per increment. After each verb, add 403 tests before moving on.

- [x] **21. Member list own tasks**
  - [x] Implement: list tasks the current user created **or** is assigned, on their team only.
  - [x] Security: automated 403 if a member requests another member’s task by id.
  - [x] Docs: list endpoint in `docs/api.md`.

- [x] **22. Create task**
  - [x] Implement: both roles can create. Member assignee defaults to self and cannot assign to another person. Server sets creator, team, timestamps.
  - [x] Security: 403 if a member sets assignee to someone else. 400 if assignee is not on the team.
  - [x] Docs: create contract in `docs/api.md`.

- [x] **23. Update task**
  - [x] Implement: status and fields per PRD edit rules (`TO_DO` | `IN_PROGRESS` | `COMPLETED`). Member: created or assigned. Lead: any team task.
  - [x] Security: 403 outside those rules. Blank title → 400.
  - [x] Docs: update contract in `docs/api.md`.

- [x] **24. Lead list, filter, assign**
  - [x] Implement: lead lists all team tasks; filter by member and status; assign/reassign (including self). Off-team assignee → 400.
  - [x] Security: member hitting the lead list or assign API → 403.
  - [x] Docs: lead list/assign in `docs/api.md`.

- [x] **25. Delete task**
  - [x] Implement: lead can delete any team task. Member can delete only tasks they created that are **not** assigned to someone else.
  - [x] Security: 403 for every other delete.
  - [x] Docs: delete rules in `docs/api.md`.

- [x] **26. Wire task UI to live API**
  - [x] Implement: My Tasks and lead task list use the live API. Drop task mocks.
  - [x] Security: 401 with no/expired token sends the user to login. Do not trust client role for API calls.
  - [x] Docs: mark task screens as live in `docs/frontend.md`.

---



## Phase 7 — Standup

- [x] **27. Upsert today’s standup**
  - [x] Implement: one row per user per UTC date. At least one of Done / Doing / Blockers required (else 400). Second submit today updates the same row.
  - [x] Security: author and team set by the server. Unique `(user_id, standup_date)` enforced in the database.
  - [x] Docs: upsert + UTC rule in `docs/api.md`.

- [x] **28. Member standup history**
  - [x] Implement: member reads own history. Today is editable; past days are read-only.
  - [x] Security: edit of a past day → 403. Member cannot read another user’s history.
  - [x] Docs: history endpoint in `docs/api.md`.

- [x] **29. Lead standups by date**
  - [x] Implement: lead reads all team entries for a selected date and sees who is missing.
  - [x] Security: member calling this endpoint → 403.
  - [x] Docs: lead standup board in `docs/api.md`.

- [x] **30. Wire standup UI to live API**
  - [x] Implement: member form + history and lead date board use the live API. Drop standup mocks.
  - [x] Security: same 401/403 behavior as tasks. Do not send `team_id` from the client.
  - [x] Docs: mark standup screens as live in `docs/frontend.md`.

---



## Phase 8 — Dashboard

This is the lead job-to-be-done.

- [x] **31. Dashboard aggregates API**
  - [x] Implement: `GET` per-member `TO_DO`/`IN_PROGRESS` counts, team status mix, 7-day completion rate, today’s standup presence (submitted vs missing).
  - [x] Security: `TEAM_LEAD` only. Scope by authenticated `team_id`.
  - [x] Docs: how each count is defined (must match the PRD) in `docs/api.md`.

- [x] **32. Wire lead dashboard**
  - [x] Implement: dashboard uses the live API. Empty team renders zeros and an empty standup list, not an error. Refresh on page load / explicit refetch (no websocket).
  - [x] Security: member UI must not expose the dashboard route; API still 403s if called.
  - [x] Docs: mark dashboard as live in `docs/frontend.md`.

- [x] **33. Member dashboard 403**
  - [x] Implement: automated test that a member calling dashboard aggregates gets **403**.
  - [x] Security: test must use a real member JWT, not a missing-token 401.
  - [x] Docs: note the test in `docs/ci.md` / `docs/api.md`.

---



## Phase 9 — MVP done gate

Maps to Success Metrics in [requirements.md](./requirements.md). Done means these are true on seed data in a deployed (or production-like) environment.

- [x] **34. Manual walkthrough**
  - [x] Implement: seeded lead can answer “who is overloaded?” and “who has not stood up today?” without leaving the app. Seeded member can create a task and submit today’s standup in one sitting.
  - [x] Security: walkthrough uses HTTPS deployed URLs, not a locally disabled Security config.
  - [x] Docs: record the walkthrough result (pass/fail) in `docs/deploy.md` or a short `docs/acceptance.md`. Do not rewrite the PRD.

- [x] **35. RBAC suite green in CI**
  - [x] Implement: member cannot read another member’s tasks, another member’s standup, or lead dashboard aggregates. Assign/delete/list rules from the PRD covered.
  - [x] Security: suite fails CI if any of those return 200.
  - [x] Docs: list the RBAC tests in `docs/ci.md`.

- [x] **36. Freshness and prod hardening**
  - [x] Implement: after a status change or standup submit, reload dashboard and confirm totals match the database. No live push.
  - [x] Security: secrets only in Dokploy env. H2 off in prod. Recheck CORS, HTTPS, security headers, and that `/api/health` remains the only unauthenticated GET.
  - [x] Docs: check off success metrics with a note pointing at this TODO. Do not rewrite [requirements.md](./requirements.md).

---



## Do not do

These are out of scope for this MVP. Do not add them as later TODO items.

- Public registration, email invites, join codes, password reset, or email of any kind
- Multiple teams per user, multiple teams per lead, or workspaces
- Comments, attachments, notifications, @mentions
- WebSockets / live updates
- Report export (PDF/CSV)
- Hourly time tracking or task-level percent complete
- Custom statuses, labels, epics, sprints, or projects-within-the-team
- OAuth / SSO
- A single “implement RBAC” ticket (RBAC belongs on each feature increment above)

