# Frontend

Vite + React + TypeScript + Shadcn/ui. Phase 3 is **mocked**: screens exist so later work swaps in-memory data for live API calls.

## Auth mode

`AUTH_MODE` in [`web/src/config/auth-mode.ts`](../web/src/config/auth-mode.ts) is `'mock'`.

| Mode | Meaning |
| --- | --- |
| `mock` (now) | Login sets a client-only session (`userId`, `displayName`, `role`). No password is stored. No JWT is minted. Nothing is sent to the API. |
| `live` (Phase 5) | Login will call `POST /api/auth/login`. Role comes from the server. Mock login must not remain a production path. |

The mock session lives in `sessionStorage` under `task-management.mock-session`. That is convenience for refresh, not persistence of credentials.

**Do not** attach the mock role to API requests. There are no API requests in this increment.

Mock role is **not** authorization. Route guards only hide UI.

## Shell

[`AppShell`](../web/src/components/layout/AppShell.tsx) is the Shadcn/ui chrome: product name, mock badge, nav, user menu.

Until mock login, the shell does **not** assume a role. Unauthenticated users see “Not signed in” and the login page. No member or lead nav.

After login:

- `TEAM_MEMBER`: My Tasks, Today standup
- `TEAM_LEAD`: Dashboard, Team tasks, My Tasks, Today standup

## Routes

| Path | Who | Screen |
| --- | --- | --- |
| `/login` | guests | Mock login (seeded users, labeled mock) |
| `/` | signed-in | Redirect: member → `/tasks`, lead → `/dashboard` |
| `/tasks` | member and lead | My Tasks (created or assigned) |
| `/standup` | member and lead | Today’s standup (UTC) |
| `/dashboard` | lead only | Team dashboard |
| `/team/tasks` | lead only | Team list, filter, assign |

Guards:

- No session → `/login`
- Member opens `/dashboard` or `/team/tasks` → `/tasks` with a note that hiding is not a 403

## Mock screens

Seeded users (same team, in-memory):

- Casey Lead (`TEAM_LEAD`)
- Alex Member (`TEAM_MEMBER`)
- Bailey Member (`TEAM_MEMBER`)

### My Tasks

List tasks the current user created or is assigned. Create with title (required), optional description and due date. Assignee is always self on this screen. Status is `TO_DO` | `IN_PROGRESS` | `COMPLETED`.

### Today standup

Done / Doing / Blockers. At least one field is required; an all-blank submit is an error, not success. One row per user per UTC date; a second save today updates the same row. Own history is listed; past days are read-only in the mock.

### Lead dashboard

- Per-member `TO_DO` and `IN_PROGRESS` counts (by assignee)
- Team status mix
- 7-day completion: share of all tasks whose `completedAt` is within 7 days
- Standup presence for today UTC (submitted vs missing), including the lead

Empty counts render as zeros, not an error. Numbers refresh on navigation / state change, not via websocket.

### Lead task list and assign

All team tasks. Filter by member and status. Create with an assignee on the team. Reassign from the list. Members cannot open this route in the UI.

## Mock-screen map

```
/login  →  pick seeded user (mock)
              ├─ TEAM_MEMBER → /tasks ↔ /standup
              │                    ↳ /dashboard and /team/tasks redirect away
              └─ TEAM_LEAD   → /dashboard
                                   /team/tasks
                                   /tasks
                                   /standup
```
