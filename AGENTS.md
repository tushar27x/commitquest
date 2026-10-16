# AGENTS.md

Insight Hub v2: a Spring Boot + Spring AI rewrite of the Insight Hub backend, plus a Next.js web UI in `web/`.
v1 reference (Python/FastAPI): `~/dev/insight_hub/server`. Roadmap and status: `PROGRESS.md`.
Product spec: `docs/v2-hero-plan.md` (wins over this file and `PROGRESS.md` where they disagree).

## Project index

_Last indexed: 2026-10-14, after Phase 8 (commit `a51a427`). Updated 2026-10-16 for the hero-pet plan (I0), I2 and B3 (Phase 10, `74aa41a`)._

### What it is

Hero-pet habit tracker for GitHub activity: the user's scored D&D class character has 3 hearts, XP and levels,
and loses a heart for each day without GitHub activity (rules: `docs/v2-hero-plan.md` §2).
Backend: `com.tushar27x:insightshub:0.0.1-SNAPSHOT`, Java 21, Spring Boot 4.1.1, Spring AI 2.0.0 (BOM), Maven wrapper,
virtual threads on. Web: Next.js 16 + React 19 + TypeScript + Tailwind 4 + TanStack Query in `web/` (Home screen built; Journal, Hero, Settings and Arena next).
Port of v1 (phases 0–9) plus new v2 features (2.6, 10–13, Part D). HTTP surface today:
`/api/auth/{login,callback,logout}` (public), `GET /api/user/insights` and `POST /api/user/sync` (JWT), `/actuator/health`.
Planned: `/api/user/hero`, `/hero/events`, `/hero/class`, `/preferences` (shapes in `docs/api/contract.md`).

### Status snapshot

- **Done:** Phases 0–8 (setup/config, health, JPA model steps 1–5, GitHub GraphQL client,
  insights engine + D&D archetype scoring, AI layer, GitHub OAuth + JWT, sync + Redis cache, rate limiting). PRs #2–#8 merged to `main`.
- **Open v1 issue:** B26 — v1 production still calls Groq models that were retired.
- **Done in Part D:** I0 (#9), B1 (#10), U1 (#11), B2 / Phase 9 (#12), I1 (#13), U2 (#14), I2 Testcontainers + CI (#15), B36 fix (#16, #17), U3 Home (#18), closed-eye sprites (#19), B3 / Phase 10 daily sync (#20).
- **Next up:** backend **B4** (`V5__hero.sql` + `HeroRules`) · ui **U4** · integration **I4** (wiring) and **I3** (contract tests), both after B5. Full order: `docs/agents/WORKFLOW.md`.
- **Open items:** see the latest "End of session" entry at the bottom of `PROGRESS.md`.
- Working agreement & phase details: `PROGRESS.md` (status table at the top, learning log at the bottom).

### Repo layout

| Path | Contents |
|---|---|
| `src/`, `pom.xml` | The Spring Boot backend (layout below) |
| `web/` | The Next.js web UI (from U1). `web/lib/api/types.ts` and `web/public/fixtures/` belong to the integration lane |
| `docs/v2-hero-plan.md` | The full spec: product, game rules, backend, UI, visual spec |
| `docs/api/` | `contract.md` (backend ⇄ web API) and `fixtures/*.json` (insights, hero, hero-events, preferences, hero-variants) |
| `docs/design/` | `README.md` (index), `png/` (13 mockup screens), `tokens.json`, `sprites.json`, `source/*.dc.html` (board markup, reference only) |
| `docs/agents/` | `WORKFLOW.md` (lanes, worktrees, step order) and `HANDOFF.md` (message board between the agents) |
| `.claude/agents/` | Agent definitions: `backend-engineer.md`, `ui-engineer.md`, `integration-qa.md` |
| `.github/workflows/ci.yml` | CI on every PR and push to `main`: backend `./mvnw verify` (Testcontainers), web design check + lint + test + build. No secrets |
| `e2e/` | Playwright e2e (integration lane, from I5) |

### Agent lanes

Three agents, one worktree and branch each; the developer reviews and merges every PR. Details: `docs/agents/WORKFLOW.md`.

| Agent | Worktree · branches | May edit |
|---|---|---|
| **backend** | `../ih-backend` · `backend/bN-…` | `src/main/**`, `src/test/**` (not `src/test/**/it/**`), migrations, `application.yml`, `pom.xml` main deps, its `BUGS.md` entries |
| **ui** | `../ih-ui` · `ui/uN-…` | `web/**` except `web/lib/api/types.ts` and `web/public/fixtures/**` |
| **integration** | `../ih-integration` · `int/iN-…` | `docs/**`, `PROGRESS.md`, `AGENTS.md`, `web/lib/api/types.ts`, `web/public/fixtures/**`, `e2e/**`, `.github/workflows/**`, `src/test/**/it/**`, `pom.xml` test deps |

- Changes outside your lane: post a `REQUEST` in `docs/agents/HANDOFF.md`; the owner makes them. Read `HANDOFF.md` at the start of every session.
- Agents never merge and never push to `main`. Force-push only with `--force-with-lease`, on your own open PR branch after a rebase (rule in `WORKFLOW.md`). One PR per step, then stop.

### Source layout (`src/main/java/com/tushar27x/insightshub/`)

| Package | Contents |
|---|---|
| `auth/` | `AuthController` (`/api/auth/*`, `oauth_state` cookie, constant-time state check), `AuthService` (authorize URL; code → user upsert with encrypted token → JWT), `GitHubOAuthClient` (token exchange, `GET /user`, `GET /user/emails`), `AuthProperties` (`insightshub.auth.*`: client id/secret, scopes `read:user user:email`, token TTL) |
| `ai/` | `AiReviewer` (5 generators — year in review, weekly, craft, repo summaries, character sheet — each with a static fallback), `AiInsightsService` (parallel execution, per-task timeout, crash isolation), `LlmChain`/`LlmProvider`/`ModelTier` (provider fallback chain), `AiConfig` (Groq primary + optional Gemini fallback), `AiProperties` (`insightshub.ai.*`), `CharacterSheet` |
| `config/` | `InsightsHubProperties` (`@ConfigurationProperties` record, `insightshub.*`), `ClockConfig` (injectable `Clock` bean), `SchedulingConfig` (`@EnableScheduling`; the scheduler runs on virtual threads), `CacheConfig` (`@EnableCaching`; Redis `user_insights`: 24 h, typed JSON, prefix `v2:`, transaction-aware) |
| `dev/` | `DevDataRunner` (dev-profile idempotent JPA round-trip check) |
| `entity/` | `Users`, `Insights` (`@MapsId`; stats, scores, AI texts, character sheet), enum `Archetype` (8 D&D classes) |
| `github/` | `GitHubGraphQlClient` (RestClient + `graphql/user-insights.graphql`, per-call token, rate-limit logging; `countBranchCommits` runs `graphql/branch-activity.graphql` for one day's window), `GitHubClientConfig`, `GitHubApiException`, `GitHubAuthException` (401 → Phase 10 `needs_reauth`) |
| `github/model/` | Typed records for the GraphQL responses: `GitHubUser` (incl. `id`, the node id the branch scan filters on), `UserInsightsData`, `BranchActivityData`, `GraphQlResponse` |
| `insights/` | `InsightsEngine` (pure logic: `GitHubUser` → `InsightsResult`), `UserStats` (snake_case via `@JsonNaming`, parity with v1), `HabitProfile` (15 habit signals, user's own commits only), `ScoringArchetypeClassifier` (0–100 per class, `ALGO_VERSION = 1`, ties → declaration order, all-zero → Rogue), `ArchetypeScores`, `ArchetypeClassifier` (interface) |
| `security/` | `SecurityConfig` (stateless, CSRF off, `/api/auth/**` + health public), `JwtConfig` (HS256 Nimbus encoder/decoder, issuer `insightshub`), `JwtService` (`sub`=GitHub id, `name`=login), `TokenCipher` (AES-256-GCM, GitHub id as AAD) |
| `sync/` | `SyncService` (`syncIfDue`: atomic 10-min per-user cooldown via `UsersRepository.claimSync`, used by login + refresh; `scheduledSync`: claims the same cooldown, for the daily job; `sync`: no cooldown; token → GitHub → calendar + branch scan → engine → AI → write), `ScheduledSync` (`@Scheduled` daily sync at `insightshub.sync.cron`, 03:00 UTC; one virtual thread per user, a `Semaphore` caps it at `concurrency`; per-user isolation, run summary logged), `BranchScanner` (for recent days the calendar shows as 0, counts the user's commits on any branch; `branch-scan-days`, default 2), `DayWindow` (one local day as `since`/`until` instants, DST-safe), `ActivityWriter` (contribution calendar → `daily_contributions`, first sync backfills, later syncs rewrite a 7-day overlap; branch counts), `InsightsWriter` (separate bean: `@Transactional` + `@CacheEvict` need the proxy), `SyncProperties` (`insightshub.sync.min-interval`, `concurrency`, `branch-scan-days`; `cron` read by `@Scheduled`) |
| `user/` | `UserController` (`GET /api/user/insights`, `POST /api/user/sync` → 200 / 429 + `Retry-After` / 409 / 502; user from JWT `sub` only), `InsightsQueryService` (`@Cacheable`), `InsightsResponse` (snake_case DTO, also the cached value) |
| `ratelimit/` | `@RateLimit(policy, key = IP\|USER)`, `RateLimitInterceptor` (after Spring Security; 429 + `Retry-After` + `ProblemDetail`), `RedisRateLimiter` (Lua `INCR`+`PEXPIRE`, keys `v2:rl:*`, fails open), `RateLimitConfig` (registers it; startup check that every policy exists), `RateLimitProperties` (`insightshub.rate-limit.policies`, incl. `sync-global`) |
| `repository/` | `UsersRepository` (`findByAlertsEnabledTrue`, `findDueForSync`, `findIdsForScheduledSync`: a token and no `needs_reauth`, `claimSync`), `InsightsRepository` (`@EntityGraph`), `DailyContributionsRepository` (JdbcTemplate `INSERT … ON CONFLICT` upserts: `calendar_count` from the calendar, `branch_commit_count` with `GREATEST` so it never goes down; the table is qualified with `spring.flyway.default-schema`, B36) |
| `error/` | `ApiExceptionHandler` (`@RestControllerAdvice`: every error as `application/problem+json`, `type: about:blank`, `instance` = path, validation 400s with snake_case `errors[]`, 500 without exception text), `ProblemDetails` (shared body builder), `ProblemErrorController` (replaces Boot's `/error`), `TooManyRequestsException` (429 + `Retry-After`) |

### Resources

- `application.yml` — all config; secrets via `.env` (`spring.config.import`), git-ignored.
- `db/migration/` — Flyway owns the `v2` schema: `V1__users_and_insights.sql`, `V2__archetype_classes.sql`,
  `V3__insights_sync_columns.sql`, `V4__activity.sql` (`daily_contributions` with `calendar_count` + `branch_commit_count`; `commits` deferred).
  Next: **`V5__hero.sql`** (`hero_state`, `hero_events` replacing the planned `habit_alerts`, user timezone/rest days/chosen class).
- `graphql/user-insights.graphql` — v1's query + `rateLimit`, commit `oid`/`committedDate`, `from`/`to` window, the user's node `id`.
- `graphql/branch-activity.graphql` — the branch scan: the user's commits (`history(since, until, author)`, filtered on committer date) on the 25 newest branches of their 20 most recently pushed repos.
- `prompts/*.st` — `persona.st` (supportive senior engineer, no roast) + one template per generator.

### Tests (`src/test/java/…`)

- Pure unit tests (always run): `InsightsEngineTest`, `HabitProfileTest`, `ScoringArchetypeClassifierTest`,
  `LlmChainTest`, `AiReviewerTest`, `AiInsightsServiceTest` (with `FakeChatModel`), `GitHubGraphQlClientTest` (`MockRestServiceServer`),
  `TokenCipherTest`, `JwtServiceTest`, `GitHubOAuthClientTest`, `AuthServiceTest`, `SyncServiceTest`, `InsightsWriterTest`, `CacheConfigTest`,
  `ActivityWriterTest`, `DailyContributionsRepositoryTest`, `ScheduledSyncTest`, `BranchScannerTest`, `DayWindowTest`.
- `InsightsCacheTest` — the real caching annotations through Spring proxies, with an in-memory cache instead of Redis.
- `AuthApiTest` — `@WebMvcTest` slice with the real security chain (GitHub + repositories mocked, no DB).
  Config for slices goes on a nested `@TestConfiguration`, not the test class (B29).
- `RateLimitApiTest` — the interceptor behind the real security chain with `InMemoryRateLimiter`; slice tests that include controllers need `RateLimitProperties` + a limiter bean.
- `InsightsEngineParityTest` — golden JSON against v1: `insights/v1-expected-insights.json` + `v1-parity-user.json`.
- Opt-in live tests (need env: `GITHUB_TOKEN`, `GROQ_API_KEY`): `GitHubGraphQlClientLiveTest`, `InsightsEngineLiveTest`, `AiLiveTest`, `BranchActivityLiveTest` (`GITHUB_TOKEN`: checks what `history()` filters on).
- **Integration tests** (`it/`, classes named `*IT`, run by Failsafe in `./mvnw verify`; Docker needed): `@IntegrationTest` = `@SpringBootTest` + `TestcontainersConfig` (Postgres 17 + Redis 7 via `@ServiceConnection`, test secrets generated at runtime, LLM base URL on a closed local port, the daily-sync cron off). All ITs share one context. `ApplicationContextIT` (the old `contextLoads`: Flyway migrates an empty DB, `ddl-auto: validate` passes, nothing in `public`), `UsersRepositoryIT` (atomic `claimSync`, `findIdsForScheduledSync`), `DailyContributionsRepositoryIT` (both upserts: idempotent, calendar and branch counts independent, `GREATEST`).
- B23 is fixed: Surefire and Failsafe point the datasource and Redis at unresolvable `*.invalid` hosts, so a test that boots the app without `TestcontainersConfig` fails to connect instead of reaching Neon. New context-booting tests go in `it/` with `@IntegrationTest`.

### Infrastructure & config facts

- DB: production Neon Postgres, **schema `v2`** (v1 still runs from `public` on the same DB — never touch it).
  `ddl-auto: validate`; Flyway is the only schema writer.
- Redis: remote, SSL, 2 s command/connect timeouts (so rate limiting fails open fast). Chat: Groq (OpenAI-compatible), `openai/gpt-oss-120b` primary / `gpt-oss-20b` fast,
  `reasoning_effort: low`; Gemini fallback dormant until `GEMINI_API_KEY` is set.
- Auth: GitHub OAuth App (callback `${BACKEND_URL}/api/auth/callback`), scopes `read:user user:email` (no `repo`).
  GitHub tokens are stored AES-GCM encrypted and never logged (B30); JWTs are HS256, 24 h.
- Embeddings/pgvector switched off (`spring.ai.model.embedding: none`, `vectorstore.type: none`) — stretch goal S1.

### Conventions

- One PR per step (B1–B8, U1–U6, I0–I6): three agents implement everything, including Phases 2.6 and 10–13; the developer reviews and merges (see Agent lanes).
- No AI-agent attribution in commits or PR descriptions (no co-author lines, no "generated with" footers).
- Bugs go to `BUGS.md` with the next `B` number (currently B1–B36); PR descriptions list the phase's bugs.
- No key- or token-shaped literals in code or tests, even fake ones: build test keys at runtime (B31).
- DTOs only from controllers (never entities); no secrets in logs; AI output always has a static fallback;
  the LLM never decides the archetype (code scores it, the LLM adds flavour).

### Agent tooling

- Neon agent skills (`neon`, `neon-postgres`) live in `.claude/skills/`, pinned by `skills-lock.json`
  (installed with `npx neon@latest skills -s neon -s neon-postgres -y`). Claude Code only for now.

### Commands

- `./mvnw spring-boot:run` — start (requires `.env`: Neon, Redis, `GROQ_API_KEY`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`). It uses the live Neon DB: only with the developer's OK.
- `./mvnw test` — all unit tests, offline (no DB, Redis, GitHub or LLM); `-Dtest=…` for one class.
- `./mvnw verify` — unit tests, then the `*IT` integration tests on Testcontainers. Needs Docker; never touches Neon. CI runs the same.
- `web/`: `npm run dev`, `npm run lint`, `npm run build` (from U1); `NEXT_PUBLIC_MOCK=1` serves fixtures for everything.

<!-- Remaining sections to be added as directed by the user. -->
