# Insight Hub web

Next.js 16 (App Router) + React 19 + TypeScript + Tailwind 4 + TanStack Query, in the Pixel Realm style.
Spec: `../docs/v2-hero-plan.md` §4–5. API shapes: `../docs/api/contract.md`.

## Commands

```bash
npm install
cp .env.example .env.local   # NEXT_PUBLIC_MOCK=1 runs without a backend
npm run dev                  # http://localhost:3000, sprite kit at /dev/sprites
npm run lint
npm test                     # vitest unit tests (lib/)
npm run build
```

## Layout

| Path | What |
|---|---|
| `app/theme.css` | Tailwind `@theme`, **generated** from `docs/design/tokens.json` (`npm run design`; `predev`/`prebuild` run it, `design:check` fails on drift) |
| `lib/design/sprites.json` | Copy of `docs/design/sprites.json`, made by the same script |
| `components/PixelSprite.tsx` | Renders a sprite grid as crisp SVG rects at an integer scale |
| `lib/sprites.ts` | Class and icon sprites with palettes resolved |
| `lib/api.ts` | `fetch` wrapper, throws `ApiError{status, retryAfter, problem}` (`status` 0 = network) |
| `lib/auth.ts` | JWT in localStorage (guarded), expiry check, `#token=` reader |
| `lib/queries.ts` | `useInsights`, `useHero`, `useHeroEvents`, `usePreferences`, `useSync`, `useChangeClass`, `useSavePreferences`; `sendBrowserZone` after login |
| `lib/mock.ts` | Mock mode; `PENDING_ENDPOINTS` lists calls the mock serves even with a backend (empty since B6) |
| `public/fixtures/` | Owned by the integration lane: a copy of `docs/api/fixtures/` |

## Mock mode

- `NEXT_PUBLIC_MOCK=1`: every `/api/*` call is answered from `public/fixtures/`. `POST /sync` waits 3 s.
- `?mock=<variant>` merges that entry of `hero-variants.json` over `hero.json`
  (`happy okay weak sleeping level_up fainted tier_campfire tier_keep tier_citadel class_cooldown`).
- `?mock_error=401|404|409|429|502` forces that status on the next call (once per page load). 429 sends `Retry-After: 451`.
  A call that is aborted before its response arrives doesn't use it up, so it works under `npm run dev` too, where StrictMode's double mount cancels each first fetch (B38).
- `NEXT_PUBLIC_MOCK=0`: `/api/*` goes to `BACKEND_URL` (default `http://localhost:8080`) through the rewrite in `next.config.ts` (120 s proxy timeout). Full guide: `../docs/run-locally.md`.
- The mock keeps class changes and preference saves in memory until the page reloads.
