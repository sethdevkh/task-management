# API

REST contract for the Task Management MVP. Web and API are separate origins. Dashboard endpoints are not in this phase.

## Public vs protected

| Path | Auth | Notes |
| --- | --- | --- |
| `GET /api/health` | Public | Liveness. No JWT. |
| `POST /api/auth/login` | Public | Seeded users only. **No register.** |
| `GET /api/tasks`, `GET /api/tasks/{id}` | Bearer JWT | Own tasks (created or assigned). Team from the principal. |
| `POST /api/tasks` | Bearer JWT | Both roles. Server sets creator, team, timestamps. |
| `PATCH /api/tasks/{id}` | Bearer JWT | Member: created or assigned. Lead: any team task. |
| `DELETE /api/tasks/{id}` | Bearer JWT | Lead: any team task. Member: created and not assigned to someone else. |
| `GET /api/lead/tasks` | Bearer JWT | `TEAM_LEAD` only. All team tasks; filter by assignee and status. |
| `PATCH /api/lead/tasks/{id}/assignee` | Bearer JWT | `TEAM_LEAD` only. Assign/reassign on the team. |
| `PUT /api/standups` | Bearer JWT | Upsert **today** (UTC). Author, team, and date are server-set. |
| `GET /api/standups`, `GET /api/standups/{id}` | Bearer JWT | Own history / one own row. Lead may `GET` a teammate’s row by id. |
| `PATCH /api/standups/{id}` | Bearer JWT | Author and **today** only. Past day → **403**. |
| `GET /api/lead/standups` | Bearer JWT | `TEAM_LEAD` only. Team entries for a UTC date plus who is missing. |
| Any other `/api/**` | Bearer JWT | Missing, expired, or invalid token → `401`. |
| `/api/auth/register` | Does not exist | Unauthenticated call → `401`. Do not add it. |

Role is taken from the token (copied from the user row at login). The client cannot choose a role or a `team_id`. Extra body fields such as `team_id`, `creatorId`, and timestamps are ignored.

TTL is **15 minutes** (`app.jwt.ttl`). There is no refresh token. After expiry the client must log in again.

Signing key: `JWT_SECRET` (env only, ≥ 32 bytes). Never committed.

## Login

`POST /api/auth/login`

Request:

```json
{
  "email": "casey@demo.local",
  "password": "…"
}
```

Do **not** send `team_id` or `role`. Extra fields are ignored.

Success `200`:

```json
{
  "token": "<jwt>",
  "role": "TEAM_LEAD",
  "displayName": "Casey Lead"
}
```

`role` is `TEAM_LEAD` or `TEAM_MEMBER`.

Failure `401` (unknown user and wrong password use the same body):

```json
{
  "error": "Invalid credentials"
}
```

Blank email/password → `400`, not `401`.

Passwords are checked with Spring’s `PasswordEncoder` (BCrypt). The JWT `role` and `displayName` claims come from the user record, not the request body.

### Subsequent calls

```http
Authorization: Bearer <jwt>
```

MVP stores that token in the browser and sends it as a header. That is XSS-visible. An httpOnly cookie would hide the token from JavaScript; this MVP does **not** use cookies, so CORS `Access-Control-Allow-Credentials` is off. See [frontend.md](./frontend.md).

`401` on a protected path means the client must discard the session and show login.

## CORS

Allowed origins come from `CORS_ALLOWED_ORIGINS` (comma-separated). Local default is `http://localhost:5173`. Prod **must** be the HTTPS web origin, for example `https://<web-host>`. Prod refuses `*`, empty, and `http://`.

| Setting | Value |
| --- | --- |
| Allowed origins | The web origin only |
| Allowed methods | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` |
| Allowed headers | `Authorization`, `Content-Type`, `Accept` |
| Credentials | `false` (token is a header, not a cookie) |

Browser login is a cross-origin `POST` plus an `OPTIONS` preflight. Configure the API with the web origin; configure the web build with `VITE_API_BASE_URL` pointing at the API origin. See [deploy.md](./deploy.md).

## Tasks

`team_id`, creator, and timestamps are server-set from the authenticated user. Do not send them. Status values are `TO_DO` | `IN_PROGRESS` | `COMPLETED`.

Task JSON (list item, get, create, update, assign):

```json
{
  "id": 1,
  "title": "Fix standup date display",
  "description": "",
  "status": "TO_DO",
  "assigneeId": 2,
  "assigneeDisplayName": "Alex Member",
  "creatorId": 1,
  "creatorDisplayName": "Casey Lead",
  "dueDate": "2026-09-20",
  "createdAt": "2026-09-17T08:00:00Z",
  "updatedAt": "2026-09-17T08:00:00Z",
  "completedAt": null,
  "canDelete": false
}
```

`canDelete` is a UI hint. The API still enforces delete rules on `DELETE`.

### List own tasks

`GET /api/tasks`

Returns tasks the caller **created or is assigned**, on their team only, newest first. Both roles use this for My Tasks. A lead’s own list is not the full team list.

`GET /api/tasks/{id}` returns one of those tasks. A member requesting another member’s task on the same team → **403** (real member JWT, not a missing-token 401). Unknown id or a task on another team → **404**.

### Create

`POST /api/tasks`

```json
{
  "title": "Fix standup date display",
  "description": "Show UTC date on the form.",
  "dueDate": "2026-09-20",
  "assigneeId": 2,
  "status": "TO_DO"
}
```

`title` is required. `description`, `dueDate`, `assigneeId`, and `status` are optional. Omitted status defaults to `TO_DO`. Omitted assignee defaults to the caller.

| Caller | Assignee rule |
| --- | --- |
| `TEAM_MEMBER` | Self only. Sending another user’s id → **403**. |
| `TEAM_LEAD` | Any user on the lead’s team, including self. |

Assignee not on the caller’s team (or unknown id) → **400** `{ "error": "Assignee is not on the team" }`. Blank title → **400**. Success → **201** with the task body.

### Update

`PATCH /api/tasks/{id}`

```json
{
  "title": "Fix standup date display",
  "description": "Show UTC date on the form.",
  "status": "IN_PROGRESS",
  "dueDate": "2026-09-21"
}
```

Omitted fields are left unchanged. This endpoint does **not** change assignee; use the lead assign API.

| Caller | Who they may edit |
| --- | --- |
| `TEAM_MEMBER` | Tasks they created **or** are assigned, on their team |
| `TEAM_LEAD` | Any task on their team |

Outside those rules → **403**. Blank title → **400** `{ "error": "Title is required" }`. Setting status to `COMPLETED` sets `completedAt`; leaving `COMPLETED` clears it.

### Lead list and assign

`GET /api/lead/tasks?assigneeId={id}&status={status}`

`TEAM_LEAD` only. Member JWT → **403**. Query params are optional. Response:

```json
{
  "tasks": [],
  "members": [
    { "id": 1, "displayName": "Casey Lead", "role": "TEAM_LEAD" }
  ]
}
```

`members` is the caller’s team (for filters and assign). `assigneeId` filters by assignee. `status` filters by task status.

`PATCH /api/lead/tasks/{id}/assignee`

```json
{ "assigneeId": 2 }
```

Lead may assign or reassign to any teammate, including themselves. Member JWT → **403**. Off-team or unknown assignee → **400**. Task not on the lead’s team → **404**.

### Delete

`DELETE /api/tasks/{id}` → **204**

| Caller | Allowed |
| --- | --- |
| `TEAM_LEAD` | Any task on their team |
| `TEAM_MEMBER` | Only tasks they created that are **not** assigned to someone else |

Every other delete → **403**. Missing/off-team task → **404**.

RBAC for these paths is covered by `TaskApiTest` in the `restapi` CI job.

## Standups

`team_id`, author, and timestamps are server-set from the authenticated user. Do not send them. The calendar date is **UTC**. Unique `(user_id, standup_date)` is enforced in the database; a second submit for today updates the same row.

Standup JSON (upsert, get, history item, lead submitted item):

```json
{
  "id": 1,
  "userId": 2,
  "displayName": "Alex Member",
  "standupDate": "2026-09-17",
  "done": "Shipped login",
  "doing": "Standup API",
  "blockers": "",
  "createdAt": "2026-09-17T08:00:00Z",
  "updatedAt": "2026-09-17T08:05:00Z",
  "editable": true
}
```

`editable` is a UI hint (`true` only when the caller is the author and `standupDate` is today UTC). The API still returns **403** for a past-day edit.

At least one of Done / Doing / Blockers is required after trim. All blank → **400** `{ "error": "At least one of Done, Doing, or Blockers is required" }`.

### Upsert today

`PUT /api/standups`

```json
{
  "done": "Shipped login",
  "doing": "Standup API",
  "blockers": ""
}
```

The server sets author, team, and `standupDate` to **today UTC**. Extra fields such as `team_id`, `userId`, and timestamps are ignored.

| Result | Status |
| --- | --- |
| First submit today | **201** with `Location: /api/standups/{id}` |
| Second submit today | **200**, same `id` |

If the body includes `standupDate` and it is **not** today UTC → **403**. That is how a backdated create is rejected. Do not send a date from the client; omit it and let the server use today.

### History

`GET /api/standups`

Returns the caller’s own rows, newest date first. Both roles use this for the Today standup history. A lead’s own list is not the team board.

`GET /api/standups/{id}` returns one of those rows. A member requesting another member’s standup on the same team → **403** (real member JWT, not a missing-token 401). Unknown id or a standup on another team → **404**. A lead may read a teammate’s standup by id.

### Edit today by id

`PATCH /api/standups/{id}`

Omitted Done / Doing / Blockers fields are left unchanged. The row must belong to the caller and must be **today UTC**. Past day → **403**. Another user’s row → **403**. Clearing all three fields → **400**.

### Lead standup board

`GET /api/lead/standups?date={yyyy-MM-dd}`

`TEAM_LEAD` only. Member JWT → **403**. `date` is optional and defaults to today UTC. Response:

```json
{
  "date": "2026-09-17",
  "submitted": [],
  "missing": [
    { "id": 1, "displayName": "Casey Lead", "role": "TEAM_LEAD" }
  ]
}
```

`submitted` is every team standup for that UTC date. `missing` is every user on the lead’s team with no row that day (lead included). There is no `team_id` in the response. Invalid `date` → **400**.

RBAC for these paths is covered by `StandupApiTest` in the `restapi` CI job. There is still no dashboard aggregates API.
