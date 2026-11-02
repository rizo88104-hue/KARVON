# E-Market — customer website

Next.js website for E-Market customers. Backend: `..\KARVON-PAY` (Spring Boot). Project rules: [CLAUDE.md](CLAUDE.md).

## Run locally

1. Start the backend (see `..\KARVON-PAY\README.md`) so it answers on http://localhost:8080.
2. Install and configure:

   ```bash
   npm install
   cp .env.example .env.local   # BACKEND_URL, NEXT_PUBLIC_SITE_URL, NEXT_PUBLIC_DELIVERY_DAYS
   ```

3. Start the dev server and open http://localhost:3000 (Russian: http://localhost:3000/ru):

   ```bash
   npm run dev
   ```

With the backend's `dev` profile, the SMS code for login can be read from
`http://localhost:8080/api/v1/dev/otp/{phone}` and payments are confirmed on the fake checkout page.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Dev server on port 3000 |
| `npm run build` / `npm start` | Production build / server |
| `npm run lint` | ESLint |
| `npm run typecheck` | TypeScript only |
| `npm run api` | Download the backend OpenAPI spec and regenerate `src/api/schema.d.ts` |

## Production

Docker image: `Dockerfile`; CI/CD: `.github/workflows/`. The server setup, compose file and deploy guide are in
`../KARVON-PAY/docs/deploy.md`.
