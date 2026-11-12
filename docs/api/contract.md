# CommitQuest: API contract (backend ⇄ web)

The backend and web app both implement against this file; changes to the shapes here are made alongside the fixtures and the shared types, in one PR.

The TypeScript form of this contract is `web/lib/api/types.ts`; `web/lib/api/fixtures.test.ts` checks every fixture against it and fails when `web/public/fixtures/` drifts from `docs/api/fixtures/`.

Example payloads live in `docs/api/fixtures/` and follow one continuous 14-day user story. The web app's mock mode serves the same files (copied to `web/public/fixtures/`), and contract tests on both sides assert against them.

## Conventions

- **Base path:** `/api`. The web app calls `/api/*` on its own origin, and Next `rewrites` forward it to the backend (`web/next.config.ts`, target `BACKEND_URL`, server-side, default `http://localhost:8080`), so there's no CORS. The one exception is the login link, which goes to `${NEXT_PUBLIC_BACKEND_URL}/api/auth/login` directly, because the `oauth_state` cookie must live on the backend origin.
- **Proxy timeout:** 120 s (`experimental.proxyTimeout`), above `POST /sync`'s worst case of about 90 s. Next's default is 30 s; on a timeout or an unreachable backend the proxy answers a plain-text 500 (see "(no body)" below). `e2e/wiring/proxy-check.mjs` proves it in CI. Any proxy in front of production needs the same allowance.
- **Rate-limit keys behind the proxy:** the per-IP policies are only on `/auth/login` and `/auth/callback`, which the browser calls directly. Everything proxied is keyed per user (JWT `sub`), so the proxy's single IP doesn't matter.
- **Auth:** `Authorization: Bearer <jwt>` (HS256, 24 h, `sub` = GitHub id). The user is always taken from the JWT, never from a parameter.
- **JSON:** snake_case keys. Timestamps are ISO-8601 UTC (`Instant`). Calendar dates are `YYYY-MM-DD` in the **user's timezone**.
- **Enums** are UPPER_CASE strings: archetypes `PALADIN BARBARIAN WIZARD ARTIFICER CLERIC BARD MONK ROGUE`; weekdays `MONDAY…SUNDAY`.
- **Errors (Phase 9, B2):** RFC 9457 `application/problem+json` on every error, always with all five keys: `{ "type": "about:blank", "title": <HTTP reason phrase>, "status", "detail", "instance": <request path, no query string> }`. `type` is always `about:blank`: tell errors apart by **status and endpoint**, never by `type`, `title` or `detail` text. **One exception:** `GET /user/rank`'s two 404s are told apart by `title` (see that endpoint).
- **Validation 400s** add `errors: [{ "field", "message" }]`, where `field` is the snake_case JSON name (a rule across fields, like "at most 2 rest days", uses that field's name, e.g. `rest_days`). `detail` is then "The request is invalid; see 'errors'". Spring's own 400s (malformed JSON, missing parameter) have no `errors`.
- **429:** always carries `Retry-After` in whole seconds, rounded up, at least 1. `X-RateLimit-Limit` and `X-RateLimit-Remaining` come **only** from the `@RateLimit` endpoints, on success and on 429: `/auth/login`, `/auth/callback` (5/min per IP each), `GET /user/insights` (20/min per user), `GET /user/hero` + `GET /user/hero/events` (policy `hero`, 60/min per user, shared), `GET` + `PUT /user/preferences` (policy `preferences`, 20/min per user, shared), `PUT /user/hero/class` (policy `hero-class`, 10/min per user), `GET /leaderboard` + `GET /user/rank` (policy `arena`, 30/min per user, shared) and the unsubscribe pages (policy `unsubscribe`, 10/min per IP). The `/sync` 429s don't carry them; the UI reads only `Retry-After`.

| Status | Meaning for the UI |
|---|---|
| 401 | Missing or expired JWT → clear the token, go to the title screen (keep cached data readable when offline) |
| 404 | No insights yet → "NEW GAME" (start a sync). On `/user/rank` with `title` "Not in the Arena": the hero isn't ranked → point to Settings |
| 409 | The context explains it: on `/sync`, the GitHub token was revoked → "Reconnect GitHub"; on `/hero/class`, the class isn't qualified |
| 429 | Cooldown or rate limit → countdown from `Retry-After` |
| 400 | Validation: show `errors[].message` next to `errors[].field` |
| 502 | GitHub unavailable → "The road is blocked" + RETRY |
| 500 | Anything unexpected, on any endpoint (the exception text is never returned) → treat like 502 |
| (no body) | A proxy or network failure may return no problem JSON → go by the status alone; a network error is treated like 502 |

---

## Existing endpoints (built in Phases 6–8)

### `GET /api/auth/login?private=true` (public)
302 to GitHub, and sets the `oauth_state` cookie (HttpOnly, `path=/api/auth`, 10 min). Rate limit: 5/min per IP.

**Private repos (B10, extended by B11).** B11: the default is OFF, and this link is only a request: whether it changes anything is decided at the callback, where the user is known (see "Login and the lock" under Preferences). Without the parameter, the scopes are `read:user user:email`, as always. With **`private=true`** (exactly the string `true`; anything else, or a missing parameter, means false) the authorize URL asks for **`read:user user:email repo`**. GitHub OAuth Apps have no read-only private scope: `repo` is read **and write** access to private repos, so the Settings copy must say what the grant allows even though the backend only reads.
- **Where the intent is carried:** in the `oauth_state` cookie, never in the callback URL and never in a second cookie. The cookie value is `<state>` (as today) or `<state>.private` (state is URL-safe base64, which has no `.`). The `state` sent to GitHub stays the bare random `<state>`, so GitHub never sees the intent. The callback splits the cookie at the first `.`, compares the bare state with the `state` query parameter in constant time as today, and reads the suffix as the intent. The cookie is cleared on the callback, as today. A forged or missing cookie fails the state check (400 as today), whatever the query says, so the intent can't be injected from outside.
- **Auth required?** No: `login` is public, so the parameter works for a first login and for a logged-in user re-authorizing from Settings (the web app starts it with a full-page navigation to `${NEXT_PUBLIC_BACKEND_URL}/api/auth/login?private=true`, like the normal login link).

### `GET /api/auth/callback?code&state` (public)
**Change in this plan (B5):** with `insightshub.auth.redirect-to-frontend: true` (the default in `application.yml` since B5), it 302s to **`${FRONTEND_URL}/dashboard#token=<jwt>`**, a fragment and not `?token=`. Login also runs `syncIfDue`; a failure there doesn't block login. If GitHub fails during the code exchange, it returns 502 (since B2; was 500). Rate limit: 5/min per IP.

**Granted scopes (B10).** The callback records the scopes GitHub actually granted, from the token response's `scope` field (comma-separated; it is the source of truth, because the user can untick scopes on GitHub's consent page). A token is private-capable when the list contains `repo`. The stored list replaces the previous one on **every** login, so a plain login after a private one narrows it to `read:user,user:email`. Then:
- **B11, checked first:** the user has `private_locked_until` in the future → the `private` intent is **ignored** (`include_private` untouched, scopes still recorded); the rest of this list does not apply;
- the cookie's intent was `private` **and** `repo` was granted → `include_private` is set to `true`, which is an **effective change**: it starts the 7-day lock and a full resync that bypasses the sync cooldown (see Preferences);
- the intent was `private` but `repo` was **not** granted (the user unticked it) → `include_private` is left as it was. Not an error; Settings shows the state from `GET /preferences`;
- no `private` intent → a login never changes `include_private` (a user who turned it on keeps it on; B11: do not assume a plain login narrows the grant: GitHub keeps scopes the user already granted, so `private_access` is whatever the token response's `scope` says, see "Totals when OFF" point 5);
- the redirect stays `${FRONTEND_URL}/dashboard#token=<jwt>`, with no extra fragment keys. The UI learns the outcome from `GET /preferences`;
- if the user cancels on GitHub, GitHub redirects with `error=access_denied` and no `code`: with a valid `state` this is **always a 302 to `${FRONTEND_URL}/settings`** (no token, nothing touched). Other callback 302 cases: success (`/dashboard#token=<jwt>`). A bad or missing state is still a 400.

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
Returns 200 with `fixtures/hero.json`. Before the first sync, 404 (the hero is created at the end of the first successful sync).

| Field | Type | Notes |
|---|---|---|
| `level`, `xp`, `xp_for_next` | int | `xp` is progress inside the current level. `xp_for_next = 50 × level` |
| `lives`, `max_lives` | int | `max_lives` is always 3 |
| `streak` | int | Consecutive active days. Rest days pause it |
| `streak_for_next_life` | int | 3. The UI shows "N more active days wins a heart back" as `3 - (streak % 3)` when `lives < 3` |
| `mood` | enum | `HAPPY OKAY WEAK HUNGRY SLEEPING LEVEL_UP FAINTED`. Computed on each request, never stored. Priority: `FAINTED` > `LEVEL_UP` > `SLEEPING` > `HUNGRY` > `base_mood`. `LEVEL_UP` and `FAINTED` come from the events on `last_evaluated_on`, so they show from that evaluation until the next one. `FAINTED` follows both a `LEVEL_DOWN` and a level-1 `FAINTED` event (the hearts ran out); the UI shows "LV n → n−1" only when the level really dropped. `SLEEPING`: today is a rest day with no activity yet (with activity: the base mood). `HUNGRY`: after 18:00 local, not a rest day, no activity today |
| `base_mood` | enum | `HAPPY OKAY WEAK`, from lives only (what the sprite shows under an overlay) |
| `tier` | enum | `CAMPFIRE` (1–4), `VILLAGE` (5–9), `KEEP` (10–14), `CITADEL` (15+) |
| `today` | object | `{date, active, calendar_count, branch_commit_count, day_ends_at}`. `day_ends_at` is the next local midnight as an Instant, for the countdown |
| `week` | array[7] | Monday→Sunday of the current local week: `{date, status}`, where status is `active` `missed` `rest` `today` `future`. Days before today are `active`, `missed` or (rest days only) `rest`. Today is `today`, or `rest` when today is a rest day. Later days are `future`, or `rest` for rest days. Days before sign-up are `missed` when inactive (there's no "none" status; this only shows in a new user's first week). The UI highlights today by matching `today.date`, not by status |
| `timezone` | string | IANA |
| `rest_days` | array | Weekday enums, at most 2 |
| `class` | object | `{chosen: CLASS\|null, current: CLASS, scored: CLASS, qualified: CLASS[], can_switch_at: Instant\|null}`. `current = chosen ?? scored`. `can_switch_at` is null when switching is allowed now |
| `last_evaluated_on` | date | The last day the hero job settled |

### `GET /api/user/hero/events?days=30` (JWT)
Returns 200 with `fixtures/hero-events.json`: `{ events: [{type, on_date, details}], xp_days: [{on_date, xp, streak}] }`, newest last.

- Event types: `LIFE_LOST LIFE_GAINED LEVEL_UP LEVEL_DOWN FAINTED RESTED CLASS_CHANGED`.
- `days` is between 1 and 90, default 30. The window is `today − days … today` in the user's zone (30 on Friday 16 Oct → 16 Sep … 16 Oct). Outside 1–90 → validation 400 with `errors[{field: "days"}]`; not a number → Spring's own 400.
- 404 before the first sync, like `GET /hero`.
- `xp_days` has **one row per settled day** in the window, oldest first, up to `last_evaluated_on`. `xp` is the XP that day gave (10 or 0), and `streak` is the streak after it. Inactive days have `xp: 0, streak: 0`; rest days have `xp: 0` and keep the paused streak. Days before sign-up (seeded on first login) have no rows.
- `events` are newest last. Each 0-XP day has a `RESTED` or `LIFE_LOST` event. On one day the order is `LEVEL_UP` before `LIFE_GAINED`, and `LIFE_LOST` before `LEVEL_DOWN` or `FAINTED`.

| Event | `details` |
|---|---|
| `LIFE_LOST` | `{lives, reason: "NO_ACTIVITY"}`. `lives` is what was left: 0 on the last heart, followed on the same date by `LEVEL_DOWN` or `FAINTED`, which refill the hearts. So never show it as the current state; that comes from `GET /hero` |
| `LIFE_GAINED` | `{lives, streak}` |
| `LEVEL_UP`, `LEVEL_DOWN` | `{from, to}` |
| `FAINTED`, `RESTED` | `{}` |
| `CLASS_CHANGED` | `{from, to, chosen}`: `from` and `to` are `class.current` before and after, `chosen` is the new stored value (`CLASS` or `null`). Pinning the class you already follow has `from == to` (show "Pinned <CLASS>") |

- The fixture is a 30-day story (`2026-09-16` to `2026-10-15`) that ends exactly in `hero.json`'s state; `lib/api/fixtures.test.ts` checks it against the rules above.

### `PUT /api/user/hero/class` (JWT)
Body: `{ "archetype": "ROGUE" }`, or `{ "archetype": null }` to go back to following the top score. A missing `archetype` key is the same as `null`.

| Result | Meaning |
|---|---|
| 200 | The updated `hero` object (same shape as `GET /hero`) |
| 409 | Not qualified: score < 40 (`insightshub.hero.qualify-score`) and not the top scored class |
| 429 + `Retry-After` | Within 7 days of the last change (`archetype_changed_at`) |
| 400 | Unknown archetype string: Spring's own 400 (a JSON error, no `errors[]`) |
| 404 | No hero yet (before the first sync) |

- **Order of checks (B6):** the same as the stored `chosen` → 200 no-op (no event, no cooldown change, even during the cooldown) → not qualified → 409 (it wins over the cooldown: waiting wouldn't help) → within the cooldown → 429 → the change.
- `detail` texts (for logs; the UI branches on status): 409 "ROGUE isn't qualified: it scores 33, and a class needs 40 or the top score"; 429 "The class was changed recently; it can change again at <instant>".
- Every real change starts the 7-day cooldown, including a change to `null` and **pinning** the class you already follow (`chosen` null → the current top class): the stored value changes, so it emits `CLASS_CHANGED` with `from == to`. The UI offers that as "PIN <CLASS>" rather than "BECOME <CLASS>".
- Qualification is checked against the latest scores, only at switch time: a chosen class that later drops below 40 is kept (`class.scored` then suggests the new top class).
- The change emits `CLASS_CHANGED` on the user's local today (details above). Level, XP, lives and streak don't change.
- Two racing PUTs: one wins (compare-and-set on `archetype_changed_at`). The loser re-reads the user: a 200 no-op if the winner stored the same class, otherwise 429 with `Retry-After` from the winner's change.

### `GET /api/user/preferences` · `PUT /api/user/preferences` (JWT)
Body and response: `fixtures/preferences.json`, `{timezone, rest_days[], alerts_enabled, public_profile, include_private, private_access, private_locked_until}` (the last two read-only; `private_locked_until` is B11). Variants for mock mode: `fixtures/preferences-variants.json`.

| Result | Meaning |
|---|---|
| 200 | Saved |
| 400 | A validation `ProblemDetail` with `errors[]` (below) |
| 404 | The JWT's user no longer exists |
| 429 | Rate limit, policy `preferences` (20/min per user, shared by GET and PUT), **or** (B11) the private-access lock: tell them apart by `title` ("Private access locked") |
| 409 / 502 | (B11) Only on a PUT that changes the effective private-access state: the resync found a dead token / GitHub down; nothing was saved |

- `PUT` takes the **full object**: `timezone`, `rest_days`, `alerts_enabled`, `public_profile` and `include_private` are required, and each missing one has an `errors[]` entry (`include_private`: "must not be null", like the others). A wrong JSON kind (`"include_private": "yes"`) is Spring's own 400, no `errors[]`.
- **`private_access` and `private_locked_until` are read-only.** A `PUT` may include them (the web app sends back the whole object it read) and they are **ignored**: never validated, never stored. The response always shows the server's truth.
- Messages: `timezone` "must be an IANA time zone, like Europe/Berlin" (region ids only: `+05:30` and `GMT+5` are refused), `rest_days` "at most 2 rest days" and "must not repeat a day". An unknown weekday string is a JSON error: Spring's own 400, no `errors[]`.

**`include_private`**: boolean, **default `false`**. The user's choice to let their own insights include private repos.
**`private_access`** (B10): enum `none | granted`, derived by the server from the scopes GitHub reported at the last login or sync. `granted` means the stored token has `repo`; `none` means it doesn't, or GitHub no longer accepts it. Independent of `include_private`.
**`private_locked_until`** (B11): ISO instant (UTC) or `null`, **read-only**. `users.private_changed_at + insightshub.private.change-cooldown` (default `7d`, a property like `insightshub.hero.class-cooldown`) while that is in the future; otherwise `null` (never changed, or the lock has passed). A `PUT` may send it back and it is ignored, like `private_access`.

**Effective (B11).** Private access is **effective** when `include_private` **and** `private_access = "granted"`. Only the backend's own idea of effective counts: **nothing private is ever fetched, stored or listed unless it is effective**. The four stored states:

| `include_private` | `private_access` | Effective? | UI state |
|---|---|---|---|
| false | none | no | Off (the default). Turning it on sends a PUT; the response says `none`, then the re-auth follows |
| false | granted | no | Off; GitHub still shares the scope. Nothing private is read. Turning it on needs no re-auth |
| true | none | no | **Waiting for GitHub**: "ON, but GitHub hasn't shared private repos yet" + a button to `GET /api/auth/login?private=true`. Nothing private is read, **no lock, no resync** |
| true | granted | **yes** | On and working |

##### Effective changes: resync and lock (B11 rules 3 and 4)
An **effective change** is a transition of "effective" from false to true or true to false. Each one triggers an **immediate full resync** of that user (`SyncService.sync` semantics: calendar, branch scan, engine, AI, write, hero seed), **bypassing the 10-minute sync cooldown** (the global cap `sync-global` still applies). "Full" means the activity window is rewritten in full too: the calendar, and `branch_commit_count` is **reset to the freshly scanned value** (not `GREATEST`, which would keep private branch counts after turning off). Whether it also **starts the 7-day lock** depends on the cause:

| Cause | Effective change? | Resync | Lock |
|---|---|---|---|
| `PUT include_private` flips while `private_access = granted` (either direction) | yes | yes, inline (below) | yes: `private_changed_at = now` |
| `PUT include_private: true` while `private_access = none` | **no**: it only stores the wish ("waiting") | no | no |
| `PUT include_private: false` while waiting (`true`, `none`) | no | no | no (allowed even when locked, because nothing effective changes) |
| The callback gets `repo` and the cookie intent is `private` (include_private false → true, or already true and waiting) | yes (on) | yes, in the login's sync (`syncQuietly`, bypassing the cooldown; a failure there doesn't block login but sets `private_resync_due`, and the next hero or daily job runs a full `sync` first) | yes, unless already locked (then the intent is ignored, see below) |
| Consent cancelled on GitHub (`access_denied`) | no | no | no |
| `repo` unticked on GitHub's consent page | no | no | no |
| **Scope lost** on GitHub's side (revocation, `repo` removed, org access lost) while effective, seen in any sync or at a login | yes (off), **not a user change** | yes: the detecting sync carries on with public data only; if the path is activity-only (hero job, daily refresh) it sets `private_resync_due` and the next run does a full `sync` | **no lock**, and an existing lock is untouched |
| Scope regained by a login while `include_private` is already true | yes (on) | yes | only if not already locked |
| A 401 (whole token dead) | not a private change | the normal `needs_reauth` path | none |

**The lock.** After an effective change by the user (rows 1 and 4) `private_locked_until = private_changed_at + 7d`. It applies to **both directions**. The first change ever (`private_changed_at` null) is always allowed. The lock is checked like the class cooldown (`ClassSwitch.decide`): a pure function, then a compare-and-set on `private_changed_at` (a lost race re-reads the user: a 200 no-op if the winner stored the same value, otherwise the 429 below).

**`PUT /preferences` order of checks (B11):**
1. `include_private` equals the stored value (whatever the other fields do) → the normal save, **200**, no resync, no lock, no matter if it is locked.
2. It changes, and `private_locked_until` is in the future → **429** (below). Exception: `true → false` while `private_access = none` is allowed (no effective change). **The whole PUT is refused**: no other field (`timezone`, `rest_days`…) is saved either, so the UI sends the full object as always and, on this 429, re-reads `GET /preferences`.
3. It changes and is **not** effective-changing (the two "no" rows above) → the normal save, **200**, `private_locked_until` unchanged.
4. It is effective-changing → the **resync runs inside the request**, with the new `include_private` (a fetch that already knows the target state), and **only if it succeeds** are the new `include_private`, the other fields, `private_changed_at` and the written insights committed together. Then **200** with the new object (`private_locked_until` now 7 days ahead). A failed resync commits **nothing** (no flip, no lock), so the stored insights never disagree with the toggle:

| Result | Meaning |
|---|---|
| 200 | Saved (and resynced, when it was effective-changing). Typical time: as `POST /sync`, up to about 90 s; the 120 s proxy allowance covers it. The UI shows the syncing state |
| 429 + `Retry-After`, `title` **"Private access locked"** | The lock. `Retry-After` = seconds until `private_locked_until`. `detail`: "Private repo access was changed recently; it can change again at <instant>". The UI tells it from the `preferences` rate limit (also a 429, `title` "Too Many Requests") **by the title** and disables the switch |
| 429 + `Retry-After`, `title` "Too Many Requests" | Either the rate limit (20/min) or the global sync cap being busy. Nothing was changed |
| 409 | The GitHub token is dead (`NEEDS_REAUTH`): nothing changed. Same handling as a sync 409 |
| 502 | GitHub unavailable during the resync: nothing changed, try again |
| 400 / 404 | As before |

The lock never hides the explanation behind a generic message: the UI normally disables the switch from `private_locked_until` first, and the 429 is for stale tabs.

**Edge cases of the effective-change PUT (B11 close-out):**
- **Scope lost during the PUT's own resync** (a flip to ON whose fetch finds the token without `repo`): **200**, not an error. The wish (`include_private = true`) is stored, the committed insights are public-only, `private_access` becomes `none`, and **no lock** is set (`private_locked_until` stays as it was). The UI shows the "waiting for GitHub" state from the returned object.
- **`stats.ranked_repos` stays at 5 entries** whatever the private state; private repos are never listed, only counted in totals (see "Private repo data rules").
- **Known side effect (flagged, not fixed):** a full resync with private access **off** replaces every stored `branch_commit_count` outside the freshly scanned window with 0 (it cannot tell private from public branch-only counts). Older public branch-only counts, which the calendar does not show, are therefore zeroed too. Calendar counts are rewritten from GitHub and are unaffected.

**Login and the lock.** (Waiting state, `true`/`none`: the re-auth link is **always usable**, even while locked. The callback keeps the lock untouched and the resync runs, because regaining a scope that was lost is not a user change; an ignored intent only affects an `include_private = false` user. The Settings button is therefore never disabled by the lock.) `GET /api/auth/login` is public, so at its start the backend does not know who the user is and **cannot refuse it**. The check is at the **callback**, where the user is known: with the cookie intent `private` and `private_locked_until` in the future, the intent is **ignored** (`include_private` is not changed; the scopes GitHub actually granted are still recorded truthfully, which may leave `include_private=false` with `private_access=granted`, harmless because that is not effective). The redirect stays `${FRONTEND_URL}/dashboard#token=<jwt>` with no extra key; the UI learns why from `GET /preferences` (`private_locked_until` set and `include_private` unchanged), so the web app records "I started a private re-auth" in `sessionStorage` before navigating and, after the hand-off, if the intent was not honoured, goes to `/settings` with the locked state. The Settings switch is disabled while locked, so the link is only reachable from a stale tab, except in the waiting state, where the link is shown even while locked.

**Turning OFF while locked is refused too** (the user's rule, against on/off abuse). Revoking the app at github.com/settings/applications is **outside our control**: it is not a user change in our app, it never locks and is never refused; it follows the **lost-scope** path: the next sync (or the next job run) sees `repo` gone or a 401, switches effective access off, resyncs with public data only, and sets no lock.

- **PUT `include_private: false`** (effective-changing) means that from the moment the request succeeds the backend **must not fetch private repos** anywhere (`POST /sync`, the login sync, the hero job, the daily activity refresh). `private_access` is **not** changed by it: the stored token and recorded scopes stay.
- **When GitHub later rejects private access** (token revoked, `repo` removed, org access lost): a **401** follows the existing `needs_reauth` path (409 on `POST /sync`, the hero job skips the user). If the token is alive but the response shows it lacks `repo` (`X-OAuth-Scopes`, or the private part refused with 403/`FORBIDDEN`), it is **not** an error for the user: the backend records the scopes it saw, `private_access` becomes `none`, `include_private` stays as the user set it (the UI shows "waiting for GitHub"), the sync **continues with public data only** and returns its normal 200. No `needs_reauth`, no hero event, no lock.
- Rate limit and `X-RateLimit-*` headers: unchanged (policy `preferences`).

A `rest_days` change takes effect **from the next local day**: the hero job uses the value that applied on the day it evaluates. **Browser zone on login (I4):** after the `#token` hand-off, the web app reads `GET /preferences`. If the stored `timezone` is still the default `UTC` and the browser's zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) resolves to a different zone, it sends one `PUT /preferences` with the full object and only `timezone` changed. A zone the user picked in Settings (anything but `UTC`) is never overwritten, and a failed PUT is ignored (Settings can fix it). Compare zones by what `Intl` resolves them to: Chrome reports `Asia/Calcutta` for `Asia/Kolkata`.

#### Private repo data rules (B10, reworked by B11; for the backend, the UI never sees the data)
- **The rule (B11 rule 1).** With private access **effective** (below) the user's private activity **is included** in the totals and insights. With it **not effective** there is **no private activity in the user's stored data**: insights, `daily_contributions` and the digest. The stored insights always match the state, which is why every effective change resyncs at once (below) and why a failed resync commits nothing.
- Private repo data feeds **only the owner's own insights** (`/insights`, hero activity, the digest). It never reaches `/leaderboard`, `/user/rank` or any public surface; the Arena is computed from hero rows only. `public_profile` is unrelated to `include_private`.
- **Not stored, and not sent to the LLM, still true in B11:** private repo names, READMEs, file lists and commit messages. They are read in memory to compute aggregate signals (below) and dropped. A private repo never appears in `stats.ranked_repos`, `stats.repo_details`, `stats.recent_commits` or `repo_summaries` (`InsightsEngine` filters `privateRepo()` on all four; verified in B11). **Q2 is answered:** private repos count in the totals and the aggregates only and are never listed, not even as a merged "a private repo" entry. Prompts receive only the aggregate `HabitProfile` numbers, language shares and the user's own totals.
- `GET /insights` keeps its shape; no new field.
- The B10 migration is **V9** (`V9__private_repos.sql`). B11 adds **V10** (`users.private_changed_at timestamptz null`, plus an internal `users.private_resync_due boolean not null default false`; neither is in any API body except as `private_locked_until`).

##### What we read (the Settings copy is written from this list; B11 rule 2)
Checked against `src/main/resources/graphql/*.graphql`, `HabitProfile` and `InsightsEngine` on `release/arena-private`. With private access effective, for private repos GitHub is asked for:

| What | Where | Used for | Kept? |
|---|---|---|---|
| Commit **messages** (the last 5 on each repo's default branch, full text) and each commit's date and author login | `user-insights.graphql` `repositories(first:5)` → `history(first:5)` | habit signals only: "fixes" share and "descriptive messages" share (first line of the user's own commits) | No. Only the two ratios survive |
| The **README text** | same query, `readme: object("HEAD:README.md")` | one boolean per repo: README of at least 500 characters ("documented") | No |
| The **names of the files and folders in the repo's root** (no contents) | same query, `tree: object("HEAD:")` | whether the root has infrastructure files (Dockerfile, `*.tf`, CI config…) | No |
| Language **names**, star count | same query | language count and share, `total_stars` | Aggregates only |
| Pull requests: **lines added, lines deleted** and the PR **title** | `pullRequests(first:10)` | code churn and average PR size. The title is fetched but nothing reads it | Sums only |
| Commit counts per repo and per day (no messages) | `contributionsCollection` and `branch-activity.graphql` (`history(...) { totalCount }`) | totals, the calendar, the branch scan | Counts only |

**Never fetched:** the content of any source file, diffs or patches, issue and PR bodies, comments, anything outside the list. The backend never writes to GitHub.

**Mismatch with "we only fetch the commit messages, not the actual code" (flagged for the user).** The statement is true for *code* but incomplete: besides commit messages the backend also reads each private repo's **README text** (a document file, though not code), the **root file and folder names**, and **PR titles** (unused). The Settings copy must therefore not say "only commit messages". Use the truthful wording in "Copy" below. If the user wants the stricter promise, the backend can drop the README, the root listing and the PR title for private repos at the query level (the "documented" and "infra" signals then ignore private repos). That is Open question Q5; the contract assumes the list above until it is answered.

**GitHub gives no read-only private scope.** `repo` is read **and write** access to private repos. The copy must say so ("GitHub only offers one all-or-nothing permission; we only read, and never write").

##### Totals when OFF (B11 rule 5)
GitHub's `contributionsCollection` has **no privacy filter**. Which fields can exclude private work (from GitHub's GraphQL schema; the backend confirms each with a one-off live probe before relying on it, see "Backend tests"):

| Field | Can it exclude private work when OFF? |
|---|---|
| `repositories(privacy: PUBLIC)` | **Yes** (already done in B10; also the branch scan) |
| `commitContributionsByRepository[].repository.isPrivate` (and `pullRequestContributionsByRepository`, `issueContributionsByRepository`, each with `maxRepositories: 100`, the most GitHub allows) | **Yes**: entries are per repo and flagged, so private ones can be dropped |
| `totalCommitContributions`, `totalPullRequestContributions`, `totalIssueContributions` | **No**: single numbers over all repos the token can see, so a `repo` token includes private work |
| `restrictedContributionsCount` | Only a total of what the viewer *cannot* see; no breakdown. Not usable per day |
| `contributionCalendar` (`totalContributions`, per-day `contributionCount`) | **No**: no privacy flag. If the user's GitHub profile has "Include private contributions on my profile" enabled, anonymous private counts are in the calendar **even for a token without `repo`** (they are on their public profile too) |
| `pullRequests(first:10)` (code churn) | **Yes**, if each node also selects `repository { isPrivate }` and the private ones are dropped |

**Chosen mitigation (no revocation):**
1. **Totals.** When private access is not effective, `total_commits`, `total_prs` and `total_issues` are the **sum of the per-repo lists with `isPrivate` entries dropped** (3 lists, `maxRepositories: 100`), not the `total*Contributions` numbers. When effective, use the `total*` numbers (they include private work). So a user who toggles has a visible, consistent change in the totals.
2. **Code churn.** Select `repository { isPrivate }` on each PR node; when not effective, drop private ones.
3. **Everything else** is already public-only when OFF (`privacy: PUBLIC`, in the repository list and the branch scan).
4. **Calendar, active days, streak, heatmaps, hero activity: residual limit.** They cannot be filtered per day. They contain private work in exactly one case: the user's GitHub profile setting "Include private contributions on my profile" is on, and then GitHub itself shows those counts, anonymously, on the public profile. The Settings copy says so and tells the user how to remove it on GitHub (profile → Contribution settings). This is the contract's single known exception to rule 1.
5. **Why not revoke the token when turning OFF** (`DELETE /applications/{client_id}/token` or `/grant`)? (a) It does not fix the calendar, the only real residual. (b) It would put every user who turns the switch off into `needs_reauth` and force a GitHub round trip. (c) GitHub keeps scopes the user already granted the app, so a plain re-login would most likely hand back a token that still lists `repo`, which means narrowing a token needs the *grant* revoked, i.e. a second consent. (c) also corrects B10: **a plain login does not reliably narrow `private_access` to `none`**; treat `private_access` as exactly what GitHub reports in `scope` on the last login or sync, with no promise either way. The UI must not describe the grant as removed. Q4 asks the user to confirm.
6. A user who wants GitHub-side access removed revokes the app at github.com/settings/applications. That is outside our control and is handled by the lost-scope path (401 → `needs_reauth`; a token that is alive but lacks `repo` → effective access off, public-only resync, no lock).

**Open questions (the user to answer; the contract's default in brackets):**
- **Q1** (answered by B11 rule 3) Turning off recomputes at once: yes, a full resync, no cooldown.
- **Q2** (answered above) Private repos are never listed, even merged.
- **Q3** (answered) Cancel on GitHub's consent page: 302 to `${FRONTEND_URL}/settings`, see the callback.
- **Q4** [no revocation] Is the calendar residual (private contributions already shown on the user's GitHub profile) acceptable, or should turning OFF also revoke the grant and ask for a fresh consent? Revoking costs a forced re-login and does not remove the residual.
- **Q5** [keep the list as it is] Keep reading private READMEs, root file names and commit messages for the habit signals, or drop them for private repos and make the stricter promise "we only count commits"? Dropping weakens the "documented", "infra" and "descriptive messages" signals for private work.
- **Q6** [not rewritten] Hero history (settled days, XP, level, lives, `hero_events`) earned while private access was effective is **not** recomputed when it turns off: events are an immutable log, and rewriting lives or levels would be wrong. Only the activity inputs and the insights are rewritten. Is that acceptable for "no stale data from the previous state remains"? The alternative is a hero reset, which the contract does not offer.

##### Settings copy for the private switch (B11 rule 2; true as of the list above, for the UI)
Default OFF. Shown under the switch (wording may be tightened, the facts may not):
- "Include my private repositories in my stats. Off by default."
- "What we read from private repos: commit messages (the latest few), the README text, the names of files and folders in the repo's root (not their contents), language names, pull request sizes, and commit counts. We use them only to compute your scores and habits, and we do not keep or show them: no private repo name, README, file name or commit message is stored, listed or sent to the AI."
- "We never read your code: no source files, no diffs."
- "GitHub only offers one all-or-nothing permission for private repos ('repo'), which also allows writing. We only read, and never write."
- "Private work counts in your totals and scores while this is on. When it is off, we remove it with a fresh sync."
- "Your streak and calendar come from GitHub's contribution calendar. If your GitHub profile shows private contributions ('Include private contributions on my profile'), they appear there even when this is off. You can turn that off on GitHub."
- "After you change this, it is locked for 7 days (until <date>)." Shown when `private_locked_until` is set; the switch is disabled and `aria-disabled` with the date as its description.
- "To remove our access completely, revoke the app at github.com/settings/applications."

##### Backend tests to change or add (B11)
- Change: `HeroApiTest` and `ApiContractIT` (strict/shape checks against `preferences.json`) gain `private_locked_until` (nullable instant); `PreferencesService`/`Preferences` DTO tests; `AuthApiTest`/`AuthServiceTest` (callback: locked intent ignored, effective on locks and resyncs, regained scope); `SyncServiceTest` and `ScheduledSyncTest` (a flag `private_resync_due` forces a full sync; activity-only paths set it on scope loss); `ActivityWriter`/`DailyContributionsRepository*` tests (`branch_commit_count` is reset on a full resync, not `GREATEST`); `InsightsEngineTest` (totals from the per-repo lists when not effective, from `total*` when effective; churn drops private PRs); `OpenApiDocsTest` (new field, new 429 title).
- Add: a pure `PrivateSwitch.decide` test mirroring `ClassSwitchTest` (same value no-op; locked 429; the `true → false` while `none` exception; effective flip; waiting store); `PreferencesIT`-style test (V10, CAS on `private_changed_at`, a failed resync commits nothing: no flip, no lock, insights untouched; a lost race); `ApiContractIT`: the lock 429 has `Retry-After` and title "Private access locked", the same-value PUT while locked is 200; a callback IT through the stubbed GitHub: grant → lock set → second private intent ignored.
- A one-off live probe (opt-in, like `BranchActivityLiveTest`; needs `GITHUB_TOKEN` of a user with private repos) that records, for the same window, `totalCommitContributions`, the sum of `commitContributionsByRepository` with and without `isPrivate`, `restrictedContributionsCount` and the calendar total, to confirm the table in "Totals when OFF" before the totals change ships.

##### UI states to build (B11)
Settings → the private switch: (1) **off, unlocked** (default); (2) **on, working** (`true`/`granted`); (3) **waiting for GitHub** (`true`/`none`, with the re-auth button, which stays enabled even when locked); (4) **syncing**: a PUT that changes the effective state takes up to about 90 s, so show the 3 s-plus "SYNCING" state, disable the switch, and do not optimistic-update; (5) **locked** (`private_locked_until` in the future, either value): switch disabled with the date; (6) **errors**: lock 429 (re-read preferences, show the date), 409 (the usual re-login), 502 (retry, nothing changed), rate-limit 429 (retry later); (7) after a private re-auth whose intent was ignored because of the lock: land on `/settings` with the locked state (via `sessionStorage`, see "Login and the lock"); (8) `private_lock_lost_scope`: waiting while locked, the turn-off is allowed (the contract's one exception). Mock: `?mock=<variant>` as in "Mock mode".

### `GET /api/leaderboard?archetype=MONK&page=0&size=20` (Phase 13, JWT)
Fixture: `leaderboard.json`. One page of a class's board, best first.

**Who is on a board:** heroes whose **current** class (`class.current` of `GET /hero`: `chosen ?? scored`) is that class, with `public_profile = true` (opt-in: it starts **off**) and GitHub access that works (not `needs_reauth`: the hero job skips those, so their hero would sit frozen). Ranked by `level DESC, xp DESC`; ties share a rank (1, 2, 2, 4, as SQL `RANK()`) and are listed by GitHub id, so pages never overlap. Computed per request.

- `archetype`: optional; without it, the caller's own current class. Any signed-in user can read any class's board, public or not.
- `page` (default 0) and `size` (default **20**, at most **50**).
- Response: `archetype`, `page`, `size`, `total` (heroes on the whole board; pages = `ceil(total / size)`), `entries[]`. `entries` is `[]` past the last page.
- Each entry: `rank`, `login`, `avatar_url` (`https://avatars.githubusercontent.com/u/<github id>?v=4`), `level`, `xp` (progress inside the level, as in `/hero`), `lives`, `tier` (`/hero`'s enum), `is_you` (true only on the caller's row).

| Result | Meaning |
|---|---|
| 200 | The page |
| 400 | `page < 0` or `size` outside 1–50: validation problem with `errors[{field: "page"\|"size"}]`. Not a number, or an unknown or lower-case class: Spring's own 400, no `errors[]` |
| 404 | No `archetype` given and the caller has no hero yet (before the first sync) |
| 401 / 429 | As everywhere; policy `arena` |

### `GET /api/user/rank` (Phase 13, JWT)
Fixtures: `rank.json` (200), `rank-not-in-arena.json` (404). The caller's place on their current class's board.

- 200: `archetype` (the caller's current class), `rank`, `total` (heroes on that board, the caller included), `top_percent` = `ceil(100 × rank / total)`, 1–100. Only sent when the caller is on the board, so nothing is null. The caller's page on the leaderboard is `floor((rank − 1) / size)`.
- **404, `title` "Not in the Arena"**: the caller has a hero but isn't on the board: a private profile, or GitHub access to renew (`needs_reauth`). `detail`: "Your profile isn't ranked. Turn on "Show me in the Arena" in Settings, then log in again to refresh your stats." (decided by the developer on 2026-11-01). The UI shows this as a state of the Arena, not as an error.
- **404, `title` "Not Found"**: no hero yet (before the first sync) → NEW GAME, as elsewhere.
- These two are told apart **by `title`**, the one exception to the convention above (`NOT_IN_ARENA_TITLE` in `web/lib/api/types.ts`). 401 / 429 as everywhere; policy `arena`, shared with `/leaderboard`.

### `GET` · `POST /api/notifications/unsubscribe?token=...` (Phase 12, public)
The digest email's unsubscribe link. It's opened in a browser from a mailbox, so both answer with small **`text/html`** pages (Thymeleaf), not JSON. No JWT, cookie or CSRF token: the signed token is the authorization.
- `GET` → **200**, a confirm page with a form that POSTs to the same URL with the token. It **changes nothing**: mail scanners prefetch links.
- `POST` → **200**, "Digest emails are off": sets `alerts_enabled = false`. Idempotent, and the same page for an account deleted since the email went out. Any form body is accepted (RFC 8058 sends `List-Unsubscribe=One-Click`).
- A missing, forged or expired token, or a login JWT → **400** `text/html` for both.
- Rate limit `unsubscribe`: 10/min per IP, GET and POST together. Its 429 is the interceptor's `application/problem+json` with `Retry-After`.
- Each page links to `${FRONTEND_URL}/settings`, where alerts can be turned back on (`alerts_enabled` in `/preferences` is the same flag).

**The token:** HS256 with a key derived from `JWT_SECRET`, `aud = unsubscribe`, `sub` = GitHub id, valid 1 year. Rotating `JWT_SECRET` invalidates every mailed link.

**Every digest email** has `List-Unsubscribe: <${BACKEND_URL}/api/notifications/unsubscribe?token=…>` and `List-Unsubscribe-Post: List-Unsubscribe=One-Click`, so mail clients show their own unsubscribe button.

---

## Mock mode (web)

`NEXT_PUBLIC_MOCK=1` serves:
- `insights.json`, `hero.json`, `hero-events.json` and `preferences.json` for the matching GETs (mock `PUT /preferences` echoes the body and sets `private_access` to `none`; B11: `?mock=<variant>` on the GET merges that entry of `preferences-variants.json` over `preferences.json`: `private_off_granted`, `private_granted` (on, working), `private_reauth` (waiting), `private_locked_on` and `private_locked_off` (switch disabled, locked until 2026-10-23T09:30:00Z), `private_lock_lost_scope` (waiting while an old lock runs). The mock PUT answers the lock 429 itself when the stored/served state has a `private_locked_until` in the future and `include_private` differs: `Retry-After` and `title` "Private access locked"; `?mock_error=429` still forces the plain rate limit. A PUT that flips the switch with `private_access: "granted"` takes 3 s like `POST /sync`, then returns `private_locked_until` = now + 7 days);
- `leaderboard.json` and `rank.json` for the Arena (served since U11; `?mock_arena=hidden` answers `GET /user/rank` with `rank-not-in-arena.json` as a 404, `?mock_arena=empty` and any class other than MONK give an empty board);
- `hero-variants.json` merged over `hero.json` with `?mock=<variant>` (`happy okay weak sleeping level_up fainted tier_campfire tier_keep tier_citadel class_cooldown`).

Mock `POST /sync` waits 3 s, then returns `insights.json`. To exercise the error states, `?mock_error=401|404|409|429|502` forces that status on the next call.
