# Running Insight Hub v2 locally

Two ways to run it: the web app alone on fixtures (no backend, no risk), or the full stack against your real services.

## A. Web app only, on fixtures

```bash
cd web
cp .env.example .env.local      # NEXT_PUBLIC_MOCK=1
npm install
npm run dev                     # http://localhost:3000
```

Mock mode plays the fixture story (Friday 16 Oct 2026, 18:20 in Asia/Kolkata) and serves every `/api/*` call from `web/public/fixtures/`. Add to any URL:
- `?mock=<variant>` for moods and tiers: `happy okay weak sleeping level_up fainted tier_campfire tier_keep tier_citadel class_cooldown`.
- `?mock_error=401|404|409|429|502` to force that status on the first call of the page load. Under `npm run dev` it currently does nothing (B38: React StrictMode spends it on a cancelled fetch); `npm run build && npx next start` works.

## B. Full stack: backend + web app

> ⚠️ `spring-boot:run` uses your **live** services: Neon (schema `v2`; v1 in `public` is never touched), Redis, GitHub OAuth and Groq.
> A login runs a real sync (GitHub plus 5 LLM calls on your Groq key). While the backend runs, the hourly hero job (at :05 UTC) settles your hero's days in Neon, and the daily sync fires at 03:00 UTC.

**Once:**
- The root `.env` (git-ignored) has `DB_USER`, `DB_PASSWD`, `REDIS_HOST`, `REDIS_PASSWD`, `JWT_SECRET`, `ENCRYPTION_KEY`, `GROQ_API_KEY`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and for local runs `FRONTEND_URL=http://localhost:3000` and `BACKEND_URL=http://localhost:8080`.
- The GitHub OAuth App's callback URL is `http://localhost:8080/api/auth/callback`.
- `web/.env.local`:
  ```bash
  NEXT_PUBLIC_MOCK=0
  NEXT_PUBLIC_BACKEND_URL=http://localhost:8080   # the login link
  # BACKEND_URL=http://localhost:8080             # where /api/* is proxied (this is the default)
  ```

**Run** (two terminals, from an up-to-date `main`):
```bash
./mvnw spring-boot:run          # backend on :8080
cd web && npm run dev           # web app on :3000, /api/* proxied to :8080
```

Open http://localhost:3000 → PRESS START → GitHub → back on `/dashboard#token=…` → `/home` (the token leaves the address bar and history). The first login syncs, which takes up to about 90 s; the proxy waits 120 s.

**How the pieces connect** (`docs/api/contract.md`, Conventions):

```
browser ──/api/*──▶ next dev :3000 ──rewrite (120 s timeout)──▶ backend :8080
browser ──PRESS START──▶ backend :8080/api/auth/login ──▶ GitHub ──▶ backend /api/auth/callback
                                       └──302 FRONTEND_URL/dashboard#token=<jwt>──▶ web app
```

**Good to know:**
- Time zone: on the first login the web app sends your browser's zone while the stored one is still the default `UTC` (#34). A zone you pick in Settings is never overwritten.
- The 10-minute sync cooldown also applies to login: logging in twice in a row shows "RESTING m:ss".
- Check the proxy without the backend: `node e2e/wiring/proxy-check.mjs` (fake backend + `next dev`, about a minute).

## C. End-to-end tests (Playwright, `e2e/`)

Nothing here touches Neon, GitHub or Groq. Once: `cd web && npm ci`, then `cd ../e2e && npm ci && npx playwright install chromium`.

```bash
cd e2e
npm run test:mock        # mock mode on a production build (port 3201): screens, moods, tiers, GET errors
npm run test:web         # the real build (port 3202), /api/* answered by Playwright from docs/api/fixtures/
npm run test:fullstack   # the real build + the backend on Testcontainers (port 18081): Docker needed
npm test                 # mock + web
```

- Each suite builds `web/` (`.next`) itself, so run them one after the other, not in parallel.
- The full-stack backend is `it/E2eBackend`: the real app on throwaway Postgres + Redis, GitHub answered in-process (`it/GitHubStub`), the LLM on a closed port (every AI text is its static fallback). Playwright stubs GitHub's authorize redirect and blocks any other request to GitHub.
- Locally, a server already on 3201/3202/18081 is reused; stop it if it runs a different build.
- A failure leaves a trace in `e2e/test-results/`: `npx playwright show-trace <path>/trace.zip`.
