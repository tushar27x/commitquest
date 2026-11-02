# Prompt for Claude Code: Insight Hub v2, hero-pet habit tracker (backend + web UI)

You'll build Insight Hub v2 end to end: the Spring Boot backend and a new Next.js web UI. They live in one repo, which you open as the working directory. Read `AGENTS.md` and `PROGRESS.md` first. They hold the project index, conventions and history. This prompt is the product and design spec agreed after those files were last updated, and **this prompt wins where they disagree**. Your first PR updates those files to match it (step 0).

## Design mockups (UI source of truth)

The approved UI designs are two claude.ai design canvases. If you can't open them, §5 below contains everything needed to rebuild them: tokens, sprite grids, moods and backdrops.

**Update 2026-11-02:** the hi-res canvas https://claude.ai/artifact/WpSMuuZevpRYoK9M99honc replaces both for the art: PNG sprite sheets (96×128, 60-frame loops, every class in every mood) and PNG backdrops, in `docs/design/art/`. Where §5 describes 16×16 grids, CSS mood overlays or CSS-drawn backdrops, `docs/design/README.md` wins. Layout, tokens and rules are unchanged (step U7).

| Canvas | Link | Boards |
|---|---|---|
| **Insight Hub: Character Design** | https://claude.ai/artifact/1qrPrfpjU94UCYh2c2C2Nb | Class roster (8 sprites) · Palette and type · Character sheet (phone, desktop) · Moods (Monk, 7 states) · Change class (phone, interactive) |
| **Insight Hub: Home & Journal** | https://claude.ai/artifact/3vjimL2VeHgExJnuB7BNQt | Home (phone, desktop) · Journal (phone) · Settings (phone, interactive) · Title screen · Home backdrop by level tier · System states |

## Companion files (in this repo)

| Path | What |
|---|---|
| `docs/design/png/` | Rendered mockups of every screen. **Look at these.** Index in `docs/design/README.md` |
| `docs/design/tokens.json`, `docs/design/sprites.json` | Colours, fonts, pixel rules, and all sprite grids as data |
| `docs/design/source/*.dc.html` | The exact markup and CSS of each mockup board (reference only, not runnable) |
| `docs/api/contract.md`, `docs/api/fixtures/*.json` | The API contract between backend and web, with fixture payloads |
| `docs/agents/WORKFLOW.md`, `docs/agents/HANDOFF.md` | How the 3 agents (backend, UI, integration) split the work. **Its step order replaces §6 below** |
| `.claude/agents/*.md` | The 3 agent definitions |

## Ground rules

- **Repo:** `tushar27x/insightsHubV2` (local checkout = the current working directory). The Spring app stays at the root as it is. The web UI goes in a new **`web/`** folder in the same repo, so one PR can change the API and the UI together.
- **Name:** keep "Insight Hub". A rename is deferred; don't rename packages, the repo or the artifact.
- **Who builds:** three Claude Code agents (backend, UI, integration) build everything, including Phases 10–13, which `AGENTS.md` currently assigns to the developer. Lanes and order are in `docs/agents/WORKFLOW.md`. The developer reviews and merges.
- **One small PR per step** against `main`. Work one step at a time, and stop after each PR for review. The developer prefers planning one day at a time.
- **Follow `AGENTS.md` conventions:**
  - No AI attribution in commits or PR descriptions (no co-author lines, no "generated with" footers).
  - Log bugs in `BUGS.md` (next number after B33) and list them in each PR.
  - No token-shaped literals in tests (B31).
  - DTOs only from controllers. No secrets in logs.
- **⚠️ B23:** running the full test suite applies Flyway migrations to the **live Neon DB**. Run targeted unit tests (`./mvnw test -Dtest=...`). Don't boot the full context against Neon without asking.
- **Next.js 16 has breaking changes.** Before writing UI code, read `web/node_modules/next/dist/docs/` for routing, `rewrites`, `next/font` and the middleware→proxy rename.

---

## 1. The product (what changed and why)

v2's purpose is a **habit tracker**, and the yearly "GitHub Wrapped" insights are secondary. An earlier world-map UI was dropped because its levels meant nothing. The new core is a Tamagotchi-style hero:

- The user's **class character** (one of 8 D&D classes, already scored by `ScoringArchetypeClassifier`) lives on a **Home** screen.
- It has **3 lives (hearts)**. A day with no GitHub activity costs a heart. When hearts run out, the hero **drops a level**.
- Showing up earns **XP**, keeps a **streak** and **wins hearts back**.
- The yearly insights (stats, repos, AI reviews) move to a **Journal** tab.
- Users can **change class** among the classes they qualify for.

**Decisions already made (don't relitigate):**

| Topic | Decision |
|---|---|
| What counts as active ("fed") | Any GitHub contribution on the contribution calendar **or** any commit authored by the user on **any branch** (branch scan, see §3) |
| Forgiveness | **User-picked rest days**, at most 2 weekdays |
| Regaining hearts | **+1 heart per 3-day streak** (max 3) |
| Class switching | Any class with score ≥ 40, plus the top-scored class; **7-day cooldown**; level and hearts are kept |
| GitHub data | GraphQL for all activity (REST only where unavoidable: OAuth, `/user/emails`). Scopes stay `read:user user:email`, **no `repo`** |
| Visual style | Original pixel-fantasy art ("Pixel Realm" palette, §5). No Nintendo/Mario or Wizards of the Coast assets or names |

---

## 2. Game rules (pure functions, exhaustively unit-tested; the backend is the source of truth)

**Day:** a calendar date in the user's timezone (`users.timezone`, IANA, set from the browser on first login). A day is **active** if `calendar_count > 0 OR branch_commit_count > 0`.

**Each day is evaluated once, after it ends**, with a 6-hour grace because GitHub data can lag:

| Day was… | Effect |
|---|---|
| Active | +10 XP · streak +1 · every 3rd consecutive active day: +1 heart (max 3) |
| A rest day with no activity | No effect. Streak paused, not broken. Event `RESTED` |
| A rest day with activity | Counts as active |
| Inactive, not a rest day | −1 heart · streak → 0 · event `LIFE_LOST` |

- **Hearts reach 0:** level −1 (minimum 1), XP back to the start of that level, hearts refill to 3. Event `LEVEL_DOWN`; at level 1 it's `FAINTED` instead.
- **Levels:** going from level *n* to *n+1* needs `50 × n` XP. No cap. A level-up emits `LEVEL_UP`.
- **Rest days:** a bitmask of at most 2 weekdays (DB CHECK). A change applies from the next day, never retroactively.
- **First login:** XP seeded from active days in the last 90 days (10 XP each, no heart losses). Hearts start at 3. Only days after sign-up can cost hearts.
- **Catch-up:** if days were missed (downtime, or the user didn't sync), evaluate them in order. Idempotent via `hero_state.last_evaluated_on`.
- **Mood** (derived, never stored):
  - 3 hearts = HAPPY · 2 = OKAY · 1 = WEAK.
  - After 18:00 local with no activity today = HUNGRY (an overlay on top of the above).
  - Rest day = SLEEPING.
  - One-off reactions: LEVEL_UP and FAINTED.
- **Class switching:** qualified = score ≥ 40 (config) or the top-scored class. 7-day cooldown via `archetype_changed_at`. If the chosen class later drops below 40, keep it and show "your habits now point to X" as a suggestion. `chosen_archetype = NULL` means "follow the top score" (the current behaviour).

---

## 3. Backend work

**Migrations** (Flyway, `v2` schema; V1–V3 exist):

- **`V4__activity.sql`** creates `daily_contributions(user_id FK→users ON DELETE CASCADE, date DATE, calendar_count INT NOT NULL DEFAULT 0, branch_commit_count INT NOT NULL DEFAULT 0, PRIMARY KEY (user_id, date))`. Write it with a native `INSERT … ON CONFLICT DO UPDATE` upsert. The `commits` table originally planned for Phase 2.6 is deferred: lazy-message detection becomes a Journal tip later.
- **`V5__hero.sql`:**
  - `users`: add `timezone VARCHAR(64) NOT NULL DEFAULT 'UTC'`, `rest_days SMALLINT NOT NULL DEFAULT 0` (CHECK: at most 2 bits set, value < 128), `chosen_archetype VARCHAR(32) NULL` (same CHECK values as `insights.archetype`), `archetype_changed_at TIMESTAMPTZ`.
  - `hero_state(user_id PK→users, level INT, xp INT, lives INT, streak INT, last_evaluated_on DATE, updated_at TIMESTAMPTZ)`.
  - `hero_events(id BIGSERIAL, user_id, type CHECK IN ('LIFE_LOST','LIFE_GAINED','LEVEL_UP','LEVEL_DOWN','FAINTED','RESTED','CLASS_CHANGED'), on_date DATE, details JSONB, digest_id BIGINT NULL, created_at, UNIQUE(user_id, type, on_date))`. This **replaces the planned `habit_alerts`** table: the digest reads unsent events.
  - `users.no_commit_days` is obsolete (hearts replace "no commits for N days"). Leave it unused, and note that in PROGRESS.

**Phase 10, daily sync and activity history:**

- After the GraphQL fetch in `SyncService.sync(...)`, upsert `daily_contributions.calendar_count` from `contributionsCollection.contributionCalendar.weeks[].contributionDays[]`. The records are `GitHubUser.ContributionDay(LocalDate date, int contributionCount)`; reuse `InsightsEngine.sortedDays(...)`. The first sync backfills the year; later syncs only touch the recent window.
- Write from a separate transactional bean, like `InsightsWriter` (self-invocation skips the proxy). Update `SyncServiceTest` for the new constructor dependency.
- A `@Scheduled` daily sync of all users with a stored token, cron in `insightshub.sync.cron`:
  - Isolate errors per user.
  - Bound concurrency on virtual threads.
  - GitHub 401 → `needs_reauth`, as in the existing behaviour.
  - Inject `Clock` everywhere.
- **Branch scan** (new `graphql/branch-activity.graphql` + `GitHubGraphQlClient.countBranchCommits(token, userNodeId, since, until)`). It exists so users who deploy from a non-default branch, work on feature branches without a PR, or work in forks still get counted:
  - Take the user's 20 most recently pushed repos: `repositories(first: 20, ownerAffiliations: [OWNER, COLLABORATOR, ORGANIZATION_MEMBER], orderBy: {field: PUSHED_AT, direction: DESC})`. Skip any repo whose `pushedAt < since`.
  - For each repo, take its 25 most recently committed branches: `refs(refPrefix: "refs/heads/", first: 25, orderBy: {field: TAG_COMMIT_DATE, direction: DESC})`, then `target { ... on Commit { history(since: $since, until: $until, author: {id: $userNodeId}) { totalCount } } }`.
  - `since`/`until` are the user's local midnight boundaries. You need the user's GraphQL node id (`viewer { id }`).
  - A commit on several branches can be counted more than once. That's fine: only `> 0` matters.
  - **Run it only for dates where `calendar_count = 0`**, which on a normal day means just today and yesterday. Log `rateLimit.cost`, as the existing client does.
  - **Verify before relying on it** whether `history(since, until)` filters on author date or committer date: a rebase rewrites the committer date.
  - Known limit: private repos are invisible (no `repo` scope). That's deliberate.
- **Verify** which timezone GitHub buckets contribution-calendar days in. If it's UTC, document how local days map onto it.

**Phase 11 → "Hero engine"** (replaces the 3 habit rules):

- A pure `HeroRules.evaluateDay(state, date, activityCounts, isRestDay) → (newState, events)`. Write the tests first. Cover:
  - every row of the rules table
  - regaining a heart at the cap
  - level-down at level 1 (faint)
  - rest-day edge cases
  - month boundaries and timezone boundaries
  - catch-up across multiple missed days
  - idempotency
- An **hourly** `@Scheduled` job selects users whose local time is past 06:00 and whose `last_evaluated_on < yesterday` (local). It applies the missed days in order and saves state + events in one transaction. The unique constraint makes re-runs harmless.

**API** (snake_case DTOs, JWT, the user always taken from the JWT `sub`):

- `GET /api/user/hero` returns: `level, xp, xp_for_next, lives, max_lives, streak, mood, today {date, active, calendar_count, branch_commit_count}, week[7] {date, status: active|missed|rest|today|future}, timezone, rest_days, class {chosen, scored, qualified[], can_switch_at}`
- `GET /api/user/hero/events?days=30` returns the timeline.
- `PUT /api/user/hero/class {archetype}` → 409 if not qualified, 429 + `Retry-After` during the cooldown.
- `GET/PUT /api/user/preferences`: `timezone` (validated IANA), `rest_days` (at most 2), `alerts_enabled`, `public_profile`. Validate with Bean Validation.
- **Auth hand-off change:** `AuthController.callback` should redirect to `FRONTEND_URL + "/dashboard#token=" + jwt` (a **fragment**, not the `?token=` query string, so the JWT never reaches server logs or history). `insightshub.auth.redirect-to-frontend: true` in dev.

**Phase 12, digest email:** one email a day per user, only when there are unsent `hero_events`. Cover `LIFE_LOST` at 1 heart left, `LEVEL_DOWN`, `FAINTED` and `LEVEL_UP`. Use the existing supportive persona (no guilt trips), reuse the AI layer with a static template fallback, add a signed unsubscribe link, and use a `NotificationChannel` interface. (`PROGRESS.md` Phase 12 has the details.)

**Phase 13, Arena:** rank by **hero level, then XP**, within the **chosen class**, for users with `public_profile`. Class scores remain the qualification and the "suggested class" signal.

**Phase 9 (errors as `ProblemDetail`, validation, logging) is still open.** Do it before or alongside the new endpoints.

---

## 4. Web UI (`web/`)

**Stack:** Next.js 16 (App Router) + React 19 + TypeScript + Tailwind 4, and TanStack Query. Use plain `fetch` (no axios), no shadcn/radix and no framer-motion: CSS `steps()` animations suit pixel art.

- `next.config.ts` rewrites `/api/:path*` → `${BACKEND_URL}/api/:path*`, so there's no CORS.
- The login link goes **directly** to `${NEXT_PUBLIC_BACKEND_URL}/api/auth/login`, because the OAuth state cookie must live on the backend origin.
- **Check the deploy host's proxy timeout** against the worst-case sync time (about 90 s). If it's shorter, call the backend directly for `/sync` and add CORS for that route only.

**Information architecture:** tabs **HOME · JOURNAL · HERO · ARENA · SETTINGS**, as a bottom bar on phone (≥ 44 px targets, `env(safe-area-inset-bottom)`) and a top nav on desktop. Mobile first, with no horizontal page scroll down to 320 px.

| Screen | Content |
|---|---|
| **Home** (the pet) | A tier backdrop that changes with level: Lv 1–4 Campfire, 5–9 Village, 10–14 Keep, 15+ Citadel. The large hero sprite with a 2-frame idle animation and mood overlay. **3 hearts**, level + XP bar, streak. A **Today card**: "Fed today ✓", or "Not fed yet: any contribution before midnight keeps your hearts" with a local countdown and an ember border when hungry. A **7-day strip** (+10 active, −1 missed, Z rest, ? today, empty future). "N more active days wins a heart back". Recent events. A SAVE (refresh) button |
| **Journal** | Tabs STATS / REPOS / REVIEWS: 4 stat cards, a 28-day calendar (labels computed from `updated_at`, because `heatmap_labels` only covers 7 days), top repos with bars + AI `repo_summaries`, and the three AI reviews on parchment |
| **Hero** | The character sheet: sprite on a pedestal, class name, habit line, AI title + lore on parchment (fallback: label + habit), and 8 segmented score bars sorted by score with mini sprites, the top class gold-ringed. **Change class** screen: qualified classes selectable, others greyed with "NEEDS 40", a confirm "BECOME <CLASS>", and the note "keeps LV and hearts, next change in 7 days" |
| **Arena** | "Under construction · Phase 13" until leaderboards land. Use different art from a padlock: padlocks are for things the user unlocks by playing |
| **Settings** | Rest-day picker (7 day buttons, at most 2; when 2 are picked the others are disabled; "changes apply from tomorrow"), timezone (auto-detected, editable), email alerts toggle, "Show me in the Arena" toggle, a one-line note that private work counts only if the GitHub profile shows private contributions, and log out |
| **Title** (`/`) | Night scene with a keep silhouette and 4 class sprites standing on grass, the "INSIGHT HUB" pixel logo, "Keep your hero alive with your commits", PRESS START (→ backend login), and "reads public activity only" |
| **`/dashboard`** | Reads `#token` from the fragment, stores it (localStorage, try/catch), clears the URL with `router.replace`, and goes to `/home` |

**Data layer (`web/lib/`):**

- `api.ts`: a `fetch` wrapper returning a typed `ApiError{status, retryAfter}`.
- `auth.ts`: get, set and clear the token, plus a JWT expiry check.
- `queries.ts`: `useInsights`, `useSync`, `useHero`, `useHeroEvents`, `usePreferences`. Query defaults: **`retry: false` on 4xx, a long `staleTime`, `refetchOnWindowFocus: false`** (`GET /insights` is limited to 20/min).
- `useHero`, `useHeroEvents` and `usePreferences` return **typed fixtures** until their endpoints exist, so the UI isn't blocked by the backend.
- `NEXT_PUBLIC_MOCK=1` serves fixtures for everything (`web/public/fixtures/`).

**System states:**

| State | Trigger | Treatment |
|---|---|---|
| Saving | `POST /sync` (up to ~90 s) | Full-screen overlay with a segmented bar and rotating class tips |
| Resting | 429 + `Retry-After` | "RESTING m:ss" countdown that disables SAVE **and** NEW GAME (a failed login sync still uses up the 10-min cooldown) |
| New game | 404 on insights | "ROLL CHARACTER" runs a sync; falls back to Resting on 429 |
| Reconnect | 409 | Banner over the cached data: "RECONNECT GITHUB" → login. **Not** game over: the JWT is still valid |
| Session ended | 401 | Clear the token → title. Offline, keep the cached data readable |
| Road blocked | 502 / network | Inline card, last data still visible, RETRY |

**Slice 2 (later, not now):** PWA plus an offline snapshot (runtime cache-first for `/_next/static/*`; never cache `/api/*` or any URL carrying a token), sound, and an iOS one-time-code login (an installed iOS PWA doesn't share localStorage with Safari).

**Accessibility:**
- Real `<button>`/`<a>` elements, `aria-label` on icon buttons, and `role="img"` + a label on charts and heart rows.
- A text alternative for charts.
- `prefers-reduced-motion` turns off every animation.
- Hover effects only under `@media (hover: hover)`, with `:active` feedback for touch.

---

## 5. Visual spec ("Pixel Realm")

This section is the written form of the two mockup canvases linked at the top. Build from it when you can't open them. **Since 2026-11-02 the sprites, moods and backdrops come from `docs/design/art/` (see `docs/design/README.md`); the grid and overlay parts below are history.**

**Fonts** (`next/font/google`): `Press Start 2P` for headings, HUD, buttons and short labels (10–24 px; 7–9 px only for tiny tags). `VT323` for body and AI text, **minimum 20 px**.

**Tokens** (Tailwind 4 `@theme`):

| Token | Hex | Use |
|---|---|---|
| night | `#13111C` | page ground (dotted `radial-gradient(#1d1a2b 1px, transparent 1px)` at 8 px) |
| stone | `#221E33` | panels, bars |
| stone-lit | `#2E2945` | selected rows, empty segments |
| outline | `#0A0910` | all pixel borders and shadows |
| bone | `#F4ECD8` | main text |
| muted | `#C9C0DC` / `#A69DBD` | secondary text |
| gold | `#F2B632` | primary action, current/selected, XP, active days |
| parchment | `#EFE0BB` + ink `#2A1D12`, heading `#6B3D16`, inner border `#C9B48A` | **AI-written text only** |
| ember | `#EFA07A` (text) / `#7A2E22` (missed-day fill) / `#C8452A` (alert glyph) | warnings, hunger, lost hearts |
| heart | `#E0503A`, highlight `#F6A08A`, empty `#4A4466` | hearts |
| meadow | `#3F7A3C` / `#468A43` (32 px conic checker), top edge `#5E9E4E` | ground |
| rest | `#3A3460` | rest-day cells |

**Class accents** (name, score bar and selection only): Paladin `#6C9CF0` · Barbarian `#EF7A52` · Wizard `#A98CF0` · Artificer `#E3A15A` · Cleric `#4FCFC1` · Bard `#EF7099` · Monk `#F5C04A` · Rogue `#9FC39F`.

**Pixel rules:**
- No border radius. Border: `box-shadow: 0 0 0 4px #0A0910`. Drop shadow: `6px 6px 0 4px #0A0910`. The pressed state translates 4 px and drops the shadow.
- 4 px spacing grid.
- Segmented bars use 10–20 cells with 2–3 px gaps.
- Parchment box: `#EFE0BB` + `box-shadow: 0 0 0 4px #0A0910, inset 0 0 0 4px #C9B48A`.
- Integer sprite scales only (×2 lists, ×3–4 small, ×6–10 hero).

**Sprites:** 16×16 string grids rendered by a `<PixelSprite grid palette scale>` component (CSS grid or SVG rects, `image-rendering: pixelated`).
- `.` is transparent.
- Shared palette: `k #1A1410` outline, `s #F0C08A` skin, `w #F4ECD8` white, `m #C9CED6` metal, `g #F2B632` gold.
- Each class's palette overrides keys as listed.

```
PALADIN  {a:#3D6FC9 tabard, b:#8A5A32 shield}
................ ......kkkk...... .....kmmmmk..... ....kmmmmmmk.... ....kmkkkkmk.... ....kmmmmmmk....
....kmmggmmk.... .....kkkkkk..... .kkk.kaaaak..m.. kbbbkaagaaak.m.. kbgbkaggggak.m.. kbbbkaagaaakkgk.
.kbk.kaaaak..k.. ..k..kmkkmk..... .....kmk.kmk.... .....kkk.kkk....

BARBARIAN {h:#D2532A hair, l:#6B4226 fur/handle}
....hhhhhhh..... ...hhhhhhhhh.... ...hkssssskh..mm ...hksksskshmmmm ....kssssssk.mmm ....kslllssk..l.
.....kssssk...l. ...kksssssskk.l. ..ksssssssssskl. ..kskssssssksk.. ..ks.kllllk.sk.. .....kgggk......
.....kllllk..... .....ksk.ksk.... .....ksk.ksk.... ....kkk...kkk...

WIZARD {a:#6A4BC4 robe/hat, b:#3F2C80 hem, l:#8A5A32 staff}
.......k........ ......kak....... ......kagk...... .....kaaak...... ....kaagaak...g. ..kkaaaaaaakk.k.
....kskssksk..l. ....kssssssk..l. ....kwwwwwwk..l. ...kawwwwwwak.l. ..kaaawwwwaaakl. ..kaaaawwaaaaks.
..kaaagaagaaak.. ..kaaaaaaaaaak.. ..kbbbbbbbbbbk.. ...kkkkkkkkkk...

ARTIFICER {h:#5A3A22, m:#9AA7B4, g:#7FD4E6 lenses, l:#8A5A32 apron, a:#B5652A, b:#3D3A4F}
................ .....kkkkkk..... ....khhhhhhk.... ...kmmkhhkmmk... ...kmgmkkmgmk... ....kssssssk....
....kskssksk.... .....kssssk..... ...kklllllkk.m.. ..kalaaaaalak.m. ..kalaggaalakmmm ..ksklaaalksk.m.
....klllllk..... ....kbbkbbk..... ....kbk.kbk..... ...kkk...kkk....

CLERIC {a:#2FA39A}
....gggggggg.... ...g........g... ....kkkkkkkk.... ...kwwwwwwwwk... ..kwwaaaaaawwk.. ..kwksssssskwk..
..kwksksskskwk.. ..kwksssssskwk.. ...kwkkkkkkwk... ..kwwwaggawwwk.. .kswwaggggawwsk. ..kwwwaggawwwk..
..kwwwaaaawwwk.. ..kwwwwwwwwwwk.. ..kaaaaaaaaaak.. ...kkkkkkkkkk...

BARD {a:#C43C62, b:#7A1F3D, h:#7A4A2A, l:#B07A3C lute, g:#E6C27A, x:#F2B632 feather}
.........xx..... ......kkkxk..... .....kaaaakk.... ....kaaaaaaak... ....khssssshk... ....kskssksk....
....kssssssk.... .....kssssk..... ...kkaawwaakk... ..kbaawwwwaabk.. .kllllkwwaaabk.. kllgglk.aaabk...
klgggllkaaabk... .kllllk.kbbk.... ..kkkk..kbkbk... .......kkk.kkk..

MONK {a:#E0812F robe, b:#8A3B1A sash}   (sleeping: replace row 4 with ....kkksskkk....)
................ .....kkkkkk..... ....kssssssk.... ....kssssssk.... ....kskssksk.... ....kssssssk....
.....kskksk..... ......kssk...... ...kkaaaabbkk... ..kaaaaabbaaak.. ..kaasskbaaaak.. ..kaasskbaaaak..
...kaaabbaaak... ...kaaaaaaaak... ....kaaaaaak.... ....kkk..kkk....

ROGUE {a:#3B4A3F hood, b:#1F2622 shadow, g:#E8D25A eyes}
......kkkk...... .....kaaaak..... ....kaaaaaak.... ...kaabbbbaak... ...kabkkkkbak... ...kabgkkgbak...
...kabbbbbbak... ....kaabbaak.... ...kkaaaaaakk... ..kaaabbbbaaak.. .mkaaabbbbaaakm. m.ksaabbbbaask.m
...kaaabbaaak... ...kbbkkkkbbk... ...kbk....kbk... ..kkk......kkk..

HEART 7×6 {h fill, w highlight}:  .hh.hh. hwhhhhh hhhhhhh .hhhhh. ..hhh.. ...h...
TREE 8×8 {g:#2F6B3A, G:#5EA85A, t:#6B4226}: ...gg... ..gggg.. .gggGgg. gggggGgg .gggggg. ..gGgg.. ...tt... ...tt...
```

(Each class is 16 rows of 16 characters, read left to right in rows of six.)

**Mood overlays** are shared across all classes, so there are no per-class mood sprites:

| Mood | Overlay |
|---|---|
| HAPPY | Gold 5×5 sparkles `..g.. ..g.. ggwgg ..g.. ..g..` |
| WEAK | `filter: saturate(.45) brightness(.85)`, a white bandage strip on the head, and a blue sweat drop `.b. bbb bwb .b.` (`#5D9AD6` / `#CFE4F7`) |
| HUNGRY | A bone-white speech bubble with a red "!" |
| SLEEPING | Closed eyes + "Z z" |
| LEVEL UP | Gold conic rays behind the sprite + "LV n!" |
| FAINTED | Sprite rotated 90° + gold "+ * +" stars + "LV n → n−1" |

**Tier backdrops** (stepped hard-stop gradients and `clip-path` shapes, no images):
- **Campfire:** night sky, a brown tent, a fire of orange and gold blocks.
- **Village:** a dusk band sky (`#241E40 → #3A2F5C → #6B4A6E → #C8705A → #E8A86A`), two houses with gabled roofs and gold-lit windows, a fence, a sand path.
- **Keep:** a blue day sky, stone walls with battlements, a central tower with a gold banner.
- **Citadel:** a golden-hour sky, three tall towers with purple roofs and gold windows.

**Fixture story** (use it everywhere in mock mode, so the screens agree):
- Monk, LV 7, 140/350 XP, 2 of 3 hearts.
- Mon and Tue active, Wed missed (lost a heart), Thu active (streak 1).
- Now it's Friday 18:20 local, not fed yet, so the hero is HUNGRY. 5:40 left.
- Sunday is a rest day.
- Class scores (the developer's real Phase 4 calibration): Monk 52, Rogue 47, Paladin 44, Barbarian 44, Wizard 33, Artificer 33, Bard 10, Cleric 0.

---

## 6. Build order (one PR each; stop for review after each)

> With three agents, follow the per-agent step tables in `docs/agents/WORKFLOW.md`. The list below is the single-agent order, kept for reference.

0. **Docs:** update `PROGRESS.md` and `AGENTS.md`:
   - The new product direction.
   - The working agreement (agent builds backend + UI, developer reviews).
   - The `web/` folder.
   - Phase 11 renamed to "Hero engine (lives, XP, levels)".
   - `habit_alerts` → `hero_events`.
   - The decisions table above.
   - Frontend no longer "out of scope".
   - Remove the "No frontend" line.
1. `V4__activity.sql` + sync writes `daily_contributions.calendar_count` (+ tests).
2. Phase 9: `ProblemDetail` everywhere, validation, logging.
3. Scheduled daily sync (Phase 10) + the branch scan.
4. `V5__hero.sql` + `HeroRules` (tests first).
5. The hourly hero job + `GET /hero`, `/hero/events`, preferences + the `#token` redirect.
6. Web scaffold in `web/`: tokens, fonts, `PixelSprite` + all sprites, data layer, mock mode.
7. Title + `/dashboard` hand-off + tab layout + system states.
8. Home (moods, tiers, today card, week strip, events).
9. Journal + Hero sheet.
10. `PUT /hero/class` + the Change-class screen.
11. Settings (preferences API).
12. Phase 12 digest.
13. Phase 13 Arena.

## 7. Definition of done

- **Backend:** targeted unit tests are green, and `HeroRules` covers every rule, edge case and idempotency case. Running the hero job twice creates no duplicate events. A 3-day outage is caught up in order.
- **Web:** `npm run lint` and `npm run build` are clean. Mock mode renders every screen and mood. Check at 320 / 375 / 768 / 1280 px. No request storm on window focus, and no retries after a 429.
- **End to end:** login lands on `/dashboard#token` → `/home` with no token in the URL. A contribution + SAVE flips the Today card to "Fed". The next morning's evaluation shows +10 XP. Class switch: unqualified → 409, during the cooldown → 429, success keeps the level.
