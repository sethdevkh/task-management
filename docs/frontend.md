# Frontend

Vite + React + TypeScript + Shadcn/ui. **Login and tasks are live** unless you explicitly opt into mock auth. Standup and dashboard **data** is still mocked.

## Auth mode

`AUTH_MODE` in [`web/src/config/auth-mode.ts`](../web/src/config/auth-mode.ts) is `'live'` unless `VITE_AUTH_MODE=mock`.

| Mode | How you get it | Meaning |
| --- | --- | --- |
| `live` (default, Docker, prod) | unset or `VITE_AUTH_MODE=live` | Login calls `POST /api/auth/login`. My Tasks and Team tasks call the task API. Role comes from the API. |
| `mock` (local UI only) | `VITE_AUTH_MODE=mock` in a gitignored `.env` | Client-only role picker and in-memory tasks. **Not** a production path. The Docker image fails the build if this is set. |

Do not keep a role dropdown on the live form. Do not mint JWTs in the browser. Do not send the client role on task API calls; the JWT is the only credential.

## Live auth flow

1. Guest opens `/login` and submits email + password (no `team_id`).
2. Web `POST`s to `{VITE_API_BASE_URL}/api/auth/login` with `credentials: 'omit'`.
3. On `200`, the client stores `{ token, role, displayName }` in `sessionStorage` (`task-management.session`) and sets `Authorization: Bearer <token>` on later API calls.
4. Redirect:
   - `TEAM_MEMBER` → `/tasks` (My Tasks)
   - `TEAM_LEAD` → `/dashboard`
5. Missing session, expired JWT (client `exp` check), or a later `401` → clear session → `/login`.

Default API origin for local Vite: `http://localhost:8080` (`VITE_API_BASE_URL`).

## Token storage (XSS)

MVP uses the **Authorization header**, not an httpOnly cookie.

| Approach | Who can read the token | CORS |
| --- | --- | --- |
| `Authorization: Bearer` (this MVP) | Any JS on the page (XSS can steal it) | No cookies. `Access-Control-Allow-Credentials` is off. Origin allow-list still required. |
| httpOnly cookie (not used) | Browser only; JS cannot read the token | Needs `credentials: 'include'`, a specific origin (never `*`), and CSRF protection. |

`sessionStorage` is cleared when the tab closes. It is not a security boundary. Treat XSS as credential theft. Prefer CSP and dependency hygiene over pretending the token is hidden.

## Shell

[`AppShell`](../web/src/components/layout/AppShell.tsx) is the Shadcn/ui chrome: product name, auth-mode badge, nav, user menu.

Unauthenticated users see “Not signed in” and the login page. No member or lead nav.

After login:

- `TEAM_MEMBER`: My Tasks, Today standup
- `TEAM_LEAD`: Dashboard, Team tasks, My Tasks, Today standup

## Routes

| Path | Who | Screen |
| --- | --- | --- |
| `/login` | guests | Live email/password (or mock picker if `VITE_AUTH_MODE=mock`) |
| `/` | signed-in | Redirect: member → `/tasks`, lead → `/dashboard` |
| `/tasks` | member and lead | My Tasks (created or assigned) — **live API** |
| `/standup` | member and lead | Today’s standup (UTC) — mock data |
| `/dashboard` | lead only | Team dashboard — mock data |
| `/team/tasks` | lead only | Team list, filter, assign — **live API** |

Guards:

- No session → `/login`
- Member opens `/dashboard` or `/team/tasks` → `/tasks` with a note that hiding is not a 403
- Task fetches that return `401` clear the session and send the user to login

## Live task screens

My Tasks and Team tasks use the API in live mode. They do not read mock tasks. They do not send `team_id` or a client-chosen role.

### My Tasks

`GET /api/tasks` for the list. Create is `POST /api/tasks` with title (required), optional description and due date. Assignee is omitted so the server defaults to self. Status changes are `PATCH /api/tasks/{id}`. Delete uses `canDelete` from the response as a hint; `DELETE` is still authorized on the server.

### Lead task list and assign

`GET /api/lead/tasks` with optional `assigneeId` and `status` query params. Create uses `POST /api/tasks` with an `assigneeId` on the team. Reassign is `PATCH /api/lead/tasks/{id}/assignee`. Members cannot open this route in the UI; a member JWT calling those lead endpoints still gets **403**.

## Mock screens (standup and dashboard)

Standup and dashboard widgets are still in-memory. Seeded users (same team):

- Casey Lead (`TEAM_LEAD`)
- Alex Member (`TEAM_MEMBER`)
- Bailey Member (`TEAM_MEMBER`)

Live login still maps those emails onto mock user ids so standup/dashboard keep working until those APIs exist. That mapping is a UI convenience, not authorization.

### Today standup

Done / Doing / Blockers. At least one field is required; an all-blank submit is an error, not success. One row per user per UTC date; a second save today updates the same row. Own history is listed; past days are read-only in the mock.

### Lead dashboard

- Per-member `TO_DO` and `IN_PROGRESS` counts (by assignee)
- Team status mix
- 7-day completion: share of all tasks whose `completedAt` is within 7 days
- Standup presence for today UTC (submitted vs missing), including the lead

Empty counts render as zeros, not an error. Numbers refresh on navigation / state change, not via websocket. Totals come from **mock seed tasks**, not the live task API.

## Screen map

```
/login  →  live POST /api/auth/login
              ├─ TEAM_MEMBER → /tasks (live) ↔ /standup (mock)
              │                    ↳ /dashboard and /team/tasks redirect away
              └─ TEAM_LEAD   → /dashboard (mock)
                                   /team/tasks (live)
                                   /tasks (live)
                                   /standup (mock)
```
