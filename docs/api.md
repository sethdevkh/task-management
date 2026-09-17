# API

REST contract for the Task Management MVP. Web and API are separate origins. There are still **no** task or standup endpoints.

## Public vs protected

| Path | Auth | Notes |
| --- | --- | --- |
| `GET /api/health` | Public | Liveness. No JWT. |
| `POST /api/auth/login` | Public | Seeded users only. **No register.** |
| Any other `/api/**` | Bearer JWT | Missing, expired, or invalid token → `401`. |
| `/api/auth/register` | Does not exist | Unauthenticated call → `401`. Do not add it. |

Role is taken from the token (copied from the user row at login). The client cannot choose a role or a `team_id`.

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
