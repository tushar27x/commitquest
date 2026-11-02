# Three-agent workflow

Three Claude Code agents work in parallel on `tushar27x/insightsHubV2`, each in its own git worktree and branch. The developer (Tushar) reviews and merges every PR. **Agents never merge and never push to `main`.** The only force-push allowed is `--force-with-lease` to your own open PR branch after a rebase (see below).

| Agent | Definition | Owns (may edit) | Must not edit |
|---|---|---|---|
| **backend** | `.claude/agents/backend-engineer.md` | `src/main/**`, `src/test/**` (except `src/test/**/it/**`), `src/main/resources/db/migration/**`, `application.yml`, `pom.xml` main dependencies, `BUGS.md` entries for its bugs | `web/**`, `docs/api/**`, `e2e/**`, `.github/**` |
| **ui** | `.claude/agents/ui-engineer.md` | `web/**` except `web/lib/api/types.ts` and `web/public/fixtures/**` | Anything outside `web/`, plus the two paths above |
| **integration** | `.claude/agents/integration-qa.md` | `docs/**` (plan, contract, fixtures, agents), `PROGRESS.md`, `AGENTS.md`, `web/lib/api/types.ts`, `web/public/fixtures/**`, `e2e/**`, `.github/workflows/**`, `src/test/**/it/**`, `pom.xml` **test** dependencies (Testcontainers etc.) | Feature code in `src/main/**` or `web/app/**`, `web/components/**` (it files bugs instead) |

**Shared files:** `pom.xml` and `application.yml` are the usual conflict spots. Whoever needs a change outside their lane writes a request in `docs/agents/HANDOFF.md`, and the owner makes it.

## Sources of truth

1. `docs/v2-hero-plan.md`: the product, rules, backend and UI spec. It wins over `PROGRESS.md` and `AGENTS.md` until the docs step updates those.
2. `docs/api/contract.md` + `docs/api/fixtures/`: the API shapes. **Only the integration agent edits them.** Backend responses and UI types must match them.
3. `docs/design/` (PNGs, `tokens.json`, `sprites.json`): the look.
4. `AGENTS.md`: repo conventions (no AI attribution in commits or PRs, log bugs in `BUGS.md`, no token-shaped literals in tests, DTOs only from controllers, no secrets in logs).

## Worktrees and branches

Run once, from the main checkout:

```bash
git fetch origin && git switch main && git pull
git worktree add ../ih-backend     -b backend/b1-activity
git worktree add ../ih-ui          -b ui/u1-scaffold
git worktree add ../ih-integration -b int/i0-docs
```

- Branch names: `backend/bN-…`, `ui/uN-…`, `int/iN-…`.
- **One PR per step.** Start each new step from fresh `main`: `git switch main && git pull && git switch -c <next>`.
- Rebase on `main` before opening a PR. Never merge `main` into a branch.
- If an open PR conflicts with `main`, rebase it and push with `git push --force-with-lease`. First `git fetch` and run `git log HEAD..origin/<branch>`: if the remote branch has commits you don't have (the developer may have used GitHub's "Update branch"), stop and ask instead of overwriting them. The lease only protects commits you haven't fetched.
- A PR description has: what changed, how it was tested, the plan step, bugs found (B-numbers), and contract changes (if any). No AI attribution lines.
- Each agent stops after opening its PR (or after pushing its branch, if `gh` isn't set up) and waits for the developer.

## Order and dependencies

```
I0 docs (merge FIRST: commits the kit, updates PROGRESS/AGENTS)
 ├── Backend: B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8
 ├── UI:      U1 → U2 → U3 → U4 → U5 → U6        (on fixtures; never blocked by the backend)
 └── Integration: I1 → I2 → I3 → I4 → I5 → I6    (follows both, wires them together)
```

| Step | Agent | Content | Needs |
|---|---|---|---|
| I0 | integration | Commit `docs/**` and `.claude/agents/**` from this kit. Update `PROGRESS.md` (Part D: hero-pet plan, the decisions table, Phase 11 → "Hero engine", `habit_alerts` → `hero_events`, the 3-agent working agreement, frontend now in scope) and `AGENTS.md` (remove "No frontend", add the `web/` and `docs/` layout, the agent lanes, the new next steps) | — |
| B1 | backend | `V4__activity.sql` + the sync writes `daily_contributions.calendar_count` (plan §3, Phase 10, first bullet) | I0 |
| B2 | backend | Phase 9: `ProblemDetail` everywhere (shape in contract.md), validation, logging, no secrets in logs | B1 |
| B3 | backend | Scheduled daily sync + **branch scan** (`branch-activity.graphql`, `countBranchCommits`). Check whether `history()` filters on author or committer date, and how GitHub buckets calendar days by timezone, and record both in `PROGRESS.md` | B2 |
| B4 | backend | `V5__hero.sql` + `HeroRules` (pure, tests first, every rule and edge case) | B3 |
| B5 | backend | Hourly hero job + `GET /hero`, `GET /hero/events`, `GET/PUT /preferences` + the `#token` fragment redirect | B4 |
| B6 | backend | `PUT /hero/class` (qualify ≥ 40 or top class, 7-day cooldown) | B5 |
| B7 | backend | Phase 12 digest from `hero_events` (`NotificationChannel`, email, AI intro with fallback, signed unsubscribe) | B6 |
| B8 | backend | Phase 13 Arena: leaderboard + my rank | B7, contract update from integration |
| U1 | ui | Scaffold `web/` (Next 16, TS, Tailwind 4, TanStack Query): tokens from `tokens.json`, fonts, `PixelSprite` + `sprites.json`, `lib/api.ts`, `lib/auth.ts`, `lib/queries.ts`, mock mode reading `web/public/fixtures/` (U1 creates that folder as a plain copy of `docs/api/fixtures/`; integration owns it from then on) | I0 |
| U2 | ui | Title `/`, `/dashboard` (reads `#token`), tab layout (bottom bar on phone, top nav on desktop), all system states | U1 |
| U3 | ui | Home: tier backdrops, hero + mood overlays, hearts, XP, today card + countdown, week strip, events | U2 |
| U4 | ui | Journal + Hero sheet | U3 |
| U5 | ui | Change class + Settings (rest-day picker at most 2, timezone, toggles) | U4 |
| U6 | ui | Arena "under construction" shell, then the real board after B8 | U5 |
| U7 | ui | Hi-res art (2026-11-02): heroes, moods, backdrops, hearts, nav icons and portraits from the PNG sheets in `docs/design/art/` instead of the 16×16 grids; every screen matched to `docs/design/source/` | U5, the art import (integration) |
| I1 | integration | Write `web/lib/api/types.ts` from the contract (the UI then imports from it). Add a test that fails if `web/public/fixtures/` drifts from `docs/api/fixtures/`, and a web test that validates every fixture against the types | U1 |
| I2 | integration | **Testcontainers** (Postgres + Redis, `@ServiceConnection`) so `./mvnw verify` never touches Neon (fixes B23). Fix `contextLoads`. Add a GitHub Actions CI workflow: backend `./mvnw verify`, web lint + build + tests | B1 |
| I3 | integration | Backend contract tests: MockMvc responses for `/insights`, `/hero`, `/hero/events`, `/preferences` match the fixture shapes (keys, types, enums) | B5 |
| I4 | integration | Wiring: Next `rewrites`, the login link, the `#token` hand-off, the proxy-timeout check for `/sync` (worst case about 90 s). Add direct CORS for `/sync` only if needed | B5, U2 |
| I5 | integration | Playwright e2e: (a) the mock-mode suite for every screen, mood and error state; (b) a local full-stack smoke test with GitHub and the LLM stubbed (WireMock or a test profile), JWT minted by the test | I3, I4, U5 |
| I6 | integration | Responsive and accessibility pass (320/375/768/1280 px, reduced motion, labels), screenshots compared with `docs/design/png/`, a release checklist | U6 |

## `docs/agents/HANDOFF.md` (the shared message board)

Each agent reads it at the start of a session and appends entries, never rewriting other agents' entries:

```
## 2026-10-17 · backend → integration · REQUEST
Contract: GET /hero should also return `last_life_lost_on` (needed for …). Proposed type: date|null.
Status: open
```

Types: `REQUEST` (needs another lane to act), `FYI` (a merged change others should know about), `BLOCKED` (waiting on a request). The owner replies under the entry and sets `Status: done`.

## Guardrails (all agents)

- ⚠️ **B23 (fixed in I2):** `./mvnw test` (unit tests) and `./mvnw verify` (plus Testcontainers ITs, Docker needed) never touch Neon: Surefire and Failsafe point the datasource at an unresolvable host. A new test that boots the app goes in `src/test/**/it/` with `@IntegrationTest`. Starting the app (`spring-boot:run`) still uses Neon: never without the developer's OK.
- Never commit `.env` or anything secret-shaped. Never log tokens.
- GitHub data comes from GraphQL. REST only for OAuth and `/user/emails`. No `repo` scope.
- The archetype is scored by code. The LLM only adds flavour text, and every AI output has a static fallback.
- Don't touch v1 (`public` schema, the `insight_hub` repo).
- When the plan is ambiguous, write the question in `HANDOFF.md` and carry on with the most reasonable reading. Say which reading you took in the PR.
