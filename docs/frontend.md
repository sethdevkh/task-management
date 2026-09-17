# Frontend

Vite + React + TypeScript + Shadcn/ui. **Login, tasks, and standups are live** unless you explicitly opt into mock auth. Dashboard **data** is still mocked.

## Auth mode

`AUTH_MODE` in [`web/src/config/auth-mode.ts`](../web/src/config/auth-mode.ts) is `'live'` unless `VITE_AUTH_MODE=mock`.

| Mode | How you get it | Meaning |
| --- | --- | --- |
| `live` (default, Docker, prod) | unset or `VITE_AUTH_MODE=live` | Login calls `POST /api/auth/login`. My Tasks, Team tasks, Today standup, and Team standups call the API. Role comes from the API. |
| `mock` (local UI only) | `VITE_AUTH_MODE=mock` in a gitignored `.env` | Client-only role picker and in-memory tasks/standups. **Not** a production path. The Docker image fails the build if this is set. |

Do not keep a role dropdown on the live form. Do not mint JWTs in the browser. Do not send the client role or `team_id` on API calls; the JWT is the only credential.

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
- `TEAM_LEAD`: Dashboard, Team tasks, Team standups, My Tasks, Today standup

## Routes

| Path | Who | Screen |
| --- | --- | --- |
| `/login` | guests | Live email/password (or mock picker if `VITE_AUTH_MODE=mock`) |
| `/` | signed-in | Redirect: member → `/tasks`, lead → `/dashboard` |
| `/tasks` | member and lead | My Tasks (created or assigned) — **live API** |
| `/standup` | member and lead | Today’s standup (UTC) + own history — **live API** |
| `/dashboard` | lead only | Team dashboard — mock data |
| `/team/tasks` | lead only | Team list, filter, assign — **live API** |
| `/team/standups` | lead only | Team standups by UTC date — **live API** |

Guards:

- No session → `/login`
- Member opens `/dashboard`, `/team/tasks`, or `/team/standups` → `/tasks` with a note that hiding is not a 403
- Task or standup fetches that return `401` clear the session and send the user to login

## Live task screens

My Tasks and Team tasks use the API in live mode. They do not read mock tasks. They do not send `team_id` or a client-chosen role.

### My Tasks

`GET /api/tasks` for the list. Create is `POST /api/tasks` with title (required), optional description and due date. Assignee is omitted so the server defaults to self. Status changes are `PATCH /api/tasks/{id}`. Delete uses `canDelete` from the response as a hint; `DELETE` is still authorized on the server.

### Lead task list and assign

`GET /api/lead/tasks` with optional `assigneeId` and `status` query params. Create uses `POST /api/tasks` with an `assigneeId` on the team. Reassign is `PATCH /api/lead/tasks/{id}/assignee`. Members cannot open this route in the UI; a member JWT calling those lead endpoints still gets **403**.

## Live standup screens

Today standup and Team standups use the API in live mode. They do not read mock standups. They do not send `team_id`, `userId`, or a client-chosen date on upsert.

### Today standup

`GET /api/standups` for own history (newest first). Save is `PUT /api/standups` with Done / Doing / Blockers. At least one field is required; an all-blank submit is an error, not success. The server stamps today UTC; a second save updates the same row. Past days are listed as read-only. `editable` from the API is a hint; editing a past day on the API still returns **403**.

### Lead standup board

`GET /api/lead/standups?date={yyyy-MM-dd}` (date defaults to today UTC). Shows submitted entries and who is missing, including the lead. Members cannot open this route in the UI; a member JWT calling the lead standup endpoint still gets **403**.

## Mock screens (dashboard)

Dashboard widgets are still in-memory. Seeded users (same team):

- Casey Lead (`TEAM_LEAD`)
- Alex Member (`TEAM_MEMBER`)
- Bailey Member (`TEAM_MEMBER`)

Live login still maps those emails onto mock user ids so the dashboard keeps working until that API exists. That mapping is a UI convenience, not authorization. Submitting a live standup does **not** change the mock dashboard presence widget.

### Lead dashboard

- Per-member `TO_DO` and `IN_PROGRESS` counts (by assignee)
- Team status mix
- 7-day completion: share of all tasks whose `completedAt` is within 7 days
- Standup presence for today UTC (submitted vs missing), including the lead

Empty counts render as zeros, not an error. Numbers refresh on navigation / state change, not via websocket. Totals come from **mock seed tasks and standups**, not the live APIs.

## Screen map

```
/login  →  live POST /api/auth/login
              ├─ TEAM_MEMBER → /tasks (live) ↔ /standup (live)
              │                    ↳ /dashboard, /team/tasks, /team/standups redirect away
              └─ TEAM_LEAD   → /dashboard (mock)
                                   /team/tasks (live)
                                   /team/standups (live)
                                   /tasks (live)
                                   /standup (live)
```
