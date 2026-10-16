# Insight Hub v2: API contract (backend ⇄ web)

**Owner: the integration agent.** The backend and UI agents implement against this file. Neither edits it directly: they propose changes in their PR description or in `docs/agents/HANDOFF.md`, and the integration agent updates the contract, the fixtures and the shared types together.

The TypeScript form of this contract is `web/lib/api/types.ts`; `web/lib/api/fixtures.test.ts` checks every fixture against it and fails when `web/public/fixtures/` drifts from `docs/api/fixtures/`.

Example payloads live in `docs/api/fixtures/` and follow the fixture story in `docs/v2-hero-plan.md` §5. The web app's mock mode serves the same files (copied to `web/public/fixtures/`), and contract tests on both sides assert against them.

## Conventions

- **Base path:** `/api`. The web app calls `/api/*` on its own origin, and Next `rewrites` forward it to the backend. The one exception is the login link, which goes to `${NEXT_PUBLIC_BACKEND_URL}/api/auth/login` directly.
- **Auth:** `Authorization: Bearer <jwt>` (HS256, 24 h, `sub` = GitHub id). The user is always taken from the JWT, never from a parameter.
- **JSON:** snake_case keys. Timestamps are ISO-8601 UTC (`Instant`). Calendar dates are `YYYY-MM-DD` in the **user's timezone**.
- **Enums** are UPPER_CASE strings: archetypes `PALADIN BARBARIAN WIZARD ARTIFICER CLERIC BARD MONK ROGUE`; weekdays `MONDAY…SUNDAY`.
- **Errors (Phase 9, B2):** RFC 9457 `application/problem+json` on every error, always with all five keys: `{ "type": "about:blank", "title": <HTTP reason phrase>, "status", "detail", "instance": <request path, no query string> }`. `type` is always `about:blank`: tell errors apart by **status and endpoint**, never by `type`, `title` or `detail` text.
- **Validation 400s** add `errors: [{ "field", "message" }]`, where `field` is the snake_case JSON name (a rule across fields, like "at most 2 rest days", uses that field's name, e.g. `rest_days`). `detail` is then "The request is invalid; see 'errors'". Spring's own 400s (malformed JSON, missing parameter) have no `errors`.
- **429:** always carries `Retry-After` in whole seconds, rounded up, at least 1. `X-RateLimit-Limit` and `X-RateLimit-Remaining` come **only** from the `@RateLimit` endpoints (`/auth/login`, `/auth/callback`, `GET /user/insights`), on success and on 429. The `/sync` 429s don't carry them; the UI reads only `Retry-After`.

| Status | Meaning for the UI |
|---|---|
| 401 | Missing or expired JWT → clear the token, go to the title screen (keep cached data readable when offline) |
| 404 | No insights yet → "NEW GAME" (start a sync) |
| 409 | The context explains it: on `/sync`, the GitHub token was revoked → "Reconnect GitHub"; on `/hero/class`, the class isn't qualified |
| 429 | Cooldown or rate limit → countdown from `Retry-After` |
| 400 | Validation: show `errors[].message` next to `errors[].field` |
| 502 | GitHub unavailable → "The road is blocked" + RETRY |
| 500 | Anything unexpected, on any endpoint (the exception text is never returned) → treat like 502 |
| (no body) | A proxy or network failure may return no problem JSON → go by the status alone; a network error is treated like 502 |

---

## Existing endpoints (built in Phases 6–8)

### `GET /api/auth/login` (public)
302 to GitHub, and sets the `oauth_state` cookie (HttpOnly, `path=/api/auth`, 10 min). Rate limit: 5/min per IP.

### `GET /api/auth/callback?code&state` (public)
**Change in this plan:** with `insightshub.auth.redirect-to-frontend: true`, it 302s to **`${FRONTEND_URL}/dashboard#token=<jwt>`**, a fragment and not `?token=`. Login also runs `syncIfDue`; a failure there doesn't block login. If GitHub fails during the code exchange, it returns 502 (since B2; was 500). Rate limit: 5/min per IP.

### `GET /api/auth/logout`
302 to `FRONTEND_URL`. The web app just drops its token; it doesn't need to call this.

### `GET /api/user/insights` (JWT)
Returns 200 with `fixtures/insights.json`. Before the first sync, 404. Rate limit: 20/min per user. Cached in Redis for 24 h and evicted on sync.

Fields: `user_name, archetype, archetype_scores{CLASS: 0-100}, stats{total_commits, total_prs, total_issues, ranked_repos[{name,count}], repo_details[{name,readme,files[]}], recent_commits[], code_churn{additions,deletions}, top_languages[], total_stars, active_days_per_year, social_ratio, last_week_heatmap[7], last_month_heatmap[28], heatmap_labels[7]}, year_in_review, weekly_review, craft_review, repo_summaries{repo: text}, character_sheet{title,lore}|null, updated_at`.

- `heatmap_labels` covers **only the 7-day week**. The UI computes the 28-day labels from `updated_at`.
- The UI must not display `repo_details` (it holds README text meant as AI input).

### `POST /api/user/sync` (JWT)
Synchronous: usually under 30 s, worst case about 90 s.

| Result | Meaning |
|---|---|
| 200 | Fresh `InsightsResponse` |
| 429 + `Retry-After` | The per-user 10-minute cooldown, **including after a failed sync**, or the global cap is busy |
| 404 | Rare: the sync wrote nothing → treat as "NEW GAME" |
| 409 | `NEEDS_REAUTH` |
| 502 | GitHub is down |

**This plan adds:** the sync also upserts today's `daily_contributions` row and runs the branch scan when needed, so `GET /hero` reflects a new contribution right away.

---

## New endpoints (Phases 10–13)

### `GET /api/user/hero` (JWT)
Returns 200 with `fixtures/hero.json`. Before the first sync, 404.

| Field | Type | Notes |
|---|---|---|
| `level`, `xp`, `xp_for_next` | int | `xp` is progress inside the current level. `xp_for_next = 50 × level` |
| `lives`, `max_lives` | int | `max_lives` is always 3 |
| `streak` | int | Consecutive active days. Rest days pause it |
| `streak_for_next_life` | int | 3. The UI shows "N more active days wins a heart back" as `3 - (streak % 3)` when `lives < 3` |
| `mood` | enum | `HAPPY OKAY WEAK HUNGRY SLEEPING LEVEL_UP FAINTED`. Computed on each request, never stored. `LEVEL_UP` and `FAINTED` show once: the day after the event, until the next evaluation |
| `base_mood` | enum | `HAPPY OKAY WEAK`, from lives only (what the sprite shows under an overlay) |
| `tier` | enum | `CAMPFIRE` (1–4), `VILLAGE` (5–9), `KEEP` (10–14), `CITADEL` (15+) |
| `today` | object | `{date, active, calendar_count, branch_commit_count, day_ends_at}`. `day_ends_at` is the next local midnight as an Instant, for the countdown |
| `week` | array[7] | Monday→Sunday of the current local week: `{date, status}`, where status is `active` `missed` `rest` `today` `future`. Days before today are `active`, `missed` or (rest days only) `rest`. Today is `today`, or `rest` when today is a rest day. Later days are `future`, or `rest` for rest days. The UI highlights today by matching `today.date`, not by status |
| `timezone` | string | IANA |
| `rest_days` | array | Weekday enums, at most 2 |
| `class` | object | `{chosen: CLASS\|null, current: CLASS, scored: CLASS, qualified: CLASS[], can_switch_at: Instant\|null}`. `current = chosen ?? scored`. `can_switch_at` is null when switching is allowed now |
| `last_evaluated_on` | date | The last day the hero job settled |

### `GET /api/user/hero/events?days=30` (JWT)
Returns 200 with `fixtures/hero-events.json`: `{ events: [{type, on_date, details}], xp_days: [{on_date, xp, streak}] }`, newest last.

- Event types: `LIFE_LOST LIFE_GAINED LEVEL_UP LEVEL_DOWN FAINTED RESTED CLASS_CHANGED`.
- `days` is between 1 and 90, default 30.
- `xp_days` has **one row per settled day** in the window, oldest first, up to `last_evaluated_on`. `xp` is the XP that day gave (10 or 0), and `streak` is the streak after it. Inactive days have `xp: 0, streak: 0`; rest days have `xp: 0` and keep the paused streak. Days before sign-up (seeded on first login) have no rows.
- `events` are newest last. Each 0-XP day has a `RESTED` or `LIFE_LOST` event. On one day the order is `LEVEL_UP` before `LIFE_GAINED`, and `LIFE_LOST` before `LEVEL_DOWN` or `FAINTED`.

| Event | `details` |
|---|---|
| `LIFE_LOST` | `{lives, reason: "NO_ACTIVITY"}`. `lives` is what was left: 0 on the last heart, followed on the same date by `LEVEL_DOWN` or `FAINTED`, which refill the hearts. So never show it as the current state; that comes from `GET /hero` |
| `LIFE_GAINED` | `{lives, streak}` |
| `LEVEL_UP`, `LEVEL_DOWN` | `{from, to}` |
| `FAINTED`, `RESTED` | `{}` |
| `CLASS_CHANGED` | Defined in B6 |

- The fixture is a 30-day story (`2026-09-16` to `2026-10-15`) that ends exactly in `hero.json`'s state; `lib/api/fixtures.test.ts` checks it against the rules above.

### `PUT /api/user/hero/class` (JWT)
Body: `{ "archetype": "ROGUE" }`, or `{ "archetype": null }` to go back to following the top score.

| Result | Meaning |
|---|---|
| 200 | The updated `hero` object |
| 409 | Not qualified (score < 40 and not the top class) |
| 429 + `Retry-After` | Within 7 days of the last change |
| 400 | Unknown archetype |

The change emits a `CLASS_CHANGED` event. Level, XP and lives don't change.

### `GET /api/user/preferences` · `PUT /api/user/preferences` (JWT)
Body and response: `fixtures/preferences.json`, `{timezone, rest_days[], alerts_enabled, public_profile}`.

| Result | Meaning |
|---|---|
| 200 | Saved |
| 400 | A validation `ProblemDetail`: invalid IANA zone, more than 2 rest days, duplicate days |

A `rest_days` change takes effect **from the next local day**: the hero job uses the value that applied on the day it evaluates. The web app sends the browser's zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) on first login if it differs from the stored zone.

### `GET /api/leaderboard?archetype=MONK&page=0&size=20` · `GET /api/user/rank` (Phase 13, JWT)
Shape to be defined by the integration agent when Phase 13 starts. Ranking is by `level DESC, xp DESC`, within the **current class**, for users with `public_profile = true`.

### `GET /api/notifications/unsubscribe?token=...` (Phase 12, public)
A signed token turns `alerts_enabled` off and returns a small HTML confirmation.

---

## Mock mode (web)

`NEXT_PUBLIC_MOCK=1` serves:
- `insights.json`, `hero.json`, `hero-events.json` and `preferences.json` for the matching GETs;
- `hero-variants.json` merged over `hero.json` with `?mock=<variant>` (`happy okay weak sleeping level_up fainted tier_campfire tier_keep tier_citadel class_cooldown`).

Mock `POST /sync` waits 3 s, then returns `insights.json`. To exercise the error states, `?mock_error=401|404|409|429|502` forces that status on the next call.
