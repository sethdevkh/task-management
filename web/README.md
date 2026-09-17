# web

Vite + React + TypeScript frontend for **Task Management**.

Login is **live** (`POST /api/auth/login`). My Tasks, Team tasks, Today standup, and Team standups use the live API. Dashboard data is still mocked. There are no frontend secrets. See [docs/frontend.md](../docs/frontend.md) and [docs/api.md](../docs/api.md).

## Prerequisites

- Node.js 24 (the version used to generate the lockfile)
- API on `http://localhost:8080` for live login

## Run locally

Copy [`.env.example`](./.env.example) to `.env` if you need to override defaults. Do not commit `.env`.

```bash
npm install
npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`). Sign in with a seeded account from [`docs/operators.md`](../docs/operators.md).

| Env | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | API origin |
| `VITE_AUTH_MODE` | `live` | Set `mock` only for UI work without the API. Not used in Docker/prod. |

## Lint and production build

```bash
npm run lint
npm run build
npm run preview
```

`npm run build` typechecks (`tsc -b`) and writes static files to `dist/`. Vite inlines `VITE_*` at build time.

## Docker

The image is a non-root nginx process serving `dist/` on port **8080**. Auth mode is forced to `live`. Pass the public API origin as a build arg:

```bash
docker build -t task-web \
  --build-arg VITE_API_BASE_URL=http://localhost:8080 \
  --build-arg VITE_AUTH_MODE=live \
  .
docker run --rm -p 8081:8080 --name task-web task-web
```

Open `http://localhost:8081`. Use host port **8081** if the API already binds 8080.
