# web

Vite + React + TypeScript frontend for **Task Management**.

This increment is a static Hello World page. It does not call the API (CORS is Phase 5). There are no frontend secrets.

## Prerequisites

- Node.js 24 (the version used to generate the lockfile)

## Run locally

```bash
npm install
npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`). You should see the product name **Task Management**.

## Lint and production build

```bash
npm run lint
npm run build
npm run preview
```

`npm run build` typechecks (`tsc -b`) and writes static files to `dist/`.

## Docker

The image is a non-root nginx process serving `dist/` on port **8080**.

```bash
docker build -t task-web .
docker run --rm -p 8080:8080 --name task-web task-web
```

Open `http://localhost:8080`. If the API container is already bound to 8080, map a different host port:

```bash
docker run --rm -p 8081:8080 --name task-web task-web
```
