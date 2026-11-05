# Insight Hub v2: Progress & Roadmap

Rewrite of the Insight Hub backend (v1: Python/FastAPI in `~/dev/insight_hub/server`, repo `tushar27x/insight_hub`) in **Spring Boot 4 + Spring AI** (repo `tushar27x/insightsHubV2`), done as a learning project. Since 2026-10-16 v2 is a **hero-pet habit tracker** with its own web UI in `web/` (Next.js), see [Part D](#part-d-hero-pet-plan-2026-10-16).
The goal is to understand the concepts, not just to reach feature parity. Each phase lists the concepts to learn, the v1 file to look at (if any), hints, and a "done when" checklist.

**Status legend:** ⬜ not started · 🟨 in progress · 👀 PR in review · ✅ done

---

## Scope & decisions

**Why v2:** a Spring Boot project for the resume, with Spring AI replacing v1's Python LLM calls. v2 ports v1, then adds new features on top.

**What v2 adds beyond the port**
1. **Habit tracker:** a daily scheduled sync of each user's GitHub activity, kept as history. Since 2026-10-16 it drives a **hero pet** with lives, XP and levels (Part D).
2. **Alerts:** hero events (lost hearts, level changes) are sent to the user in a **daily digest email**.
3. **Archetype ranking:** archetypes become scores, and users are ranked within their archetype, globally. Archetypes are D&D-style classes tied to coding habits (decided in the PR #3 review).

**Decisions (2026-10-01)**
| Topic | Decision |
|---|---|
| Habits to detect | ~~**Streak broken**, **no commits for N days**, **lazy commit messages**~~ Replaced on 2026-10-16 by the hero engine's lives, XP and levels (Part D). Lazy messages become a Journal tip later |
| Alert delivery | **Daily digest** (one email per user per day, only when there's something to report) |
| Channels | Email first, behind a `NotificationChannel` interface. Discord webhook is a stretch goal; Slack is future scope |
| GitHub data | GraphQL API for all activity data. REST only where unavoidable (OAuth code exchange, `GET /user/emails`) |
| Rankings | Global, per archetype. Friends-only ranking is future scope |
| Frontend | ~~Out of scope~~ **In scope since 2026-10-16:** a Next.js 16 web UI in `web/`, in the same repo (Part D) |
| RAG "Ask your Manager" (pgvector) | Stretch goal. pgvector starter stays in the pom, switched off |
| Database | Production Neon DB + remote Redis for dev (only the owner's data exists) |
| Sync cooldown (2026-10-14) | **Users can refresh at most every 10 minutes** (`POST /api/user/sync`, and login), instead of v1's 7-day login-only rule. GitHub isn't the limit (the sync uses the user's own token; the query costs 1 point). The shared cost is the 5 LLM calls per sync on the app's Groq key |
| GitHub scopes (2026-10-14) | **`read:user user:email`, no `repo`.** The token is stored for the daily sync, so a leak must not grant write access to private repos. Trade-off: private repos are invisible; private contributions still count in the calendar (as `restrictedContributionsCount`) |

**Workflow (2026-10-13)**
| Topic | Decision |
|---|---|
| Port (phases 3–9) | Implemented by the AI agent, **one PR per phase** against `main`, reviewed and merged by the developer |
| v2 features (2.6, 10–13) | ~~Implemented by the developer; the AI agent reviews and gives hints~~ Replaced on 2026-10-16 by the **3-agent working agreement** (Part D): backend, UI and integration agents build everything, the developer reviews and merges |
| Attribution | No AI-agent co-author lines in commits, no "generated with" footer in PR descriptions |
| Bugs | Every bug is logged in [`BUGS.md`](BUGS.md); PR descriptions list the bugs found in that phase; the developer is kept informed while debugging |
| Archetype scoring | Built by the AI agent in PR #3 after the developer's review (code scores the class; the LLM only adds flavour in Phase 5). Phase 13 keeps the leaderboards |

---

## Current status

**Owner:** 🤖 AI agent implements and opens a PR for review · 🧑 developer implements, AI agent reviews.
From Part D on, 🤖 names the lane: **backend**, **ui** or **integration** (steps B1–B8, U1–U6, I0–I6 in [`docs/agents/WORKFLOW.md`](docs/agents/WORKFLOW.md)).

| Phase | Title | Owner | Status | PR |
|------:|-------|:-----:|:------:|:--:|
| | **Part A: Port v1** | | | |
| 0 | Setup, infra & config | 🧑 | ✅ | |
| 1 | First endpoint: health ping | 🧑 | ✅ | |
| 2 | Data model for v2 (JPA), steps 1–5 | 🧑 | ✅ | |
| 2.6 | `V4__activity.sql`: `daily_contributions` (`commits` deferred) | 🤖 backend (B1) | ✅ | [#10](https://github.com/tushar27x/insightsHubV2/pull/10) [#16](https://github.com/tushar27x/insightsHubV2/pull/16) [#17](https://github.com/tushar27x/insightsHubV2/pull/17) |
| 3 | GitHub GraphQL client | 🤖 | ✅ | [#2](https://github.com/tushar27x/insightsHubV2/pull/2) |
| 4 | Insights engine (pure Java + first tests) | 🤖 | ✅ | [#3](https://github.com/tushar27x/insightsHubV2/pull/3) |
| 5 | AI layer with Spring AI | 🤖 | ✅ | [#4](https://github.com/tushar27x/insightsHubV2/pull/4) |
| 6 | Auth: GitHub OAuth + JWT, stored token & email | 🤖 | ✅ | [#6](https://github.com/tushar27x/insightsHubV2/pull/6) |
| 7 | Sync orchestration & Redis caching | 🤖 | ✅ | [#7](https://github.com/tushar27x/insightsHubV2/pull/7) |
| 8 | Rate limiting | 🤖 | ✅ | [#8](https://github.com/tushar27x/insightsHubV2/pull/8) |
| 9 | Errors, validation, logging | 🤖 backend (B2) | ✅ | [#12](https://github.com/tushar27x/insightsHubV2/pull/12) |
| | **Part B: New v2 features** | | | |
| 10 | Scheduled daily sync, activity history & branch scan | 🤖 backend (B1, B3) | ✅ (activity-only since #32; first live run pending) | [#10](https://github.com/tushar27x/insightsHubV2/pull/10) [#20](https://github.com/tushar27x/insightsHubV2/pull/20) [#32](https://github.com/tushar27x/insightsHubV2/pull/32) |
| 11 | Hero engine (lives, XP, levels) | 🤖 backend (B4–B6) | ✅ | [#22](https://github.com/tushar27x/insightsHubV2/pull/22) [#25](https://github.com/tushar27x/insightsHubV2/pull/25) [#30](https://github.com/tushar27x/insightsHubV2/pull/30) |
| 12 | Notifications: daily digest email (from `hero_events`) | 🤖 backend (B7) | ✅ (first live run pending: needs SMTP) | [#35](https://github.com/tushar27x/insightsHubV2/pull/35) |
| 13 | Arena: leaderboards by hero level within the chosen class | 🤖 backend (B8) | ✅ backend · **shelved in `web/` for the first release** (U10: tab, route and Settings switch removed) | [#40](https://github.com/tushar27x/insightsHubV2/pull/40) [#42](https://github.com/tushar27x/insightsHubV2/pull/42) |
| | **Part C: Ship it** | | | |
| 14 | Testing, API docs & deployment | 🤖 integration (I2, I3, I5, I6, deploy) | 🟨 I2, I3, I5, B9 (Swagger) done; **live since 2026-11-04** (Vercel + EC2); I6 and the backend's `prod` profile left | [#51](https://github.com/tushar27x/insightsHubV2/pull/51) [#15](https://github.com/tushar27x/insightsHubV2/pull/15) [#29](https://github.com/tushar27x/insightsHubV2/pull/29) [#36](https://github.com/tushar27x/insightsHubV2/pull/36) |
| | **Part D: Hero-pet plan** | | | |
| D0 | Docs: kit committed, `PROGRESS.md` + `AGENTS.md` updated | 🤖 integration (I0) | ✅ | [#9](https://github.com/tushar27x/insightsHubV2/pull/9) |
| D1 | Web UI in `web/` (scaffold, title, Home, Journal, Hero, Settings, Arena shell) | 🤖 ui (U1–U10) | ✅ U1–U10 (Arena shelved in U10) | [#11](https://github.com/tushar27x/insightsHubV2/pull/11) [#14](https://github.com/tushar27x/insightsHubV2/pull/14) [#18](https://github.com/tushar27x/insightsHubV2/pull/18) [#23](https://github.com/tushar27x/insightsHubV2/pull/23) [#27](https://github.com/tushar27x/insightsHubV2/pull/27) [#28](https://github.com/tushar27x/insightsHubV2/pull/28) `0ef3d35` |
| D2 | Contract, shared types, wiring, e2e | 🤖 integration (I1, I4, I5) | ✅ I1, I4, I5, sprites, hero-events, B5–B8 contract | [#13](https://github.com/tushar27x/insightsHubV2/pull/13) [#19](https://github.com/tushar27x/insightsHubV2/pull/19) [#24](https://github.com/tushar27x/insightsHubV2/pull/24) [#26](https://github.com/tushar27x/insightsHubV2/pull/26) [#31](https://github.com/tushar27x/insightsHubV2/pull/31) [#36](https://github.com/tushar27x/insightsHubV2/pull/36) |
| | **Stretch** | | | |
| S | RAG chat, Discord alerts, friends leaderboard, observability | 🧑 | ⬜ | |

**Next up** (each waits for the developer's go): **live since 2026-11-04**: web <https://insights-hub-v2-one.vercel.app>, backend <https://insighthubv2.duckdns.org> ([`docs/deploy.md`](docs/deploy.md)) · 🤖 backend: the `prod` profile (HANDOFF REQUEST: per-IP rate limits behind Caddy, Neon pool, job concurrency) · the release checklist's open items (first live run of the daily refresh and the hourly hero job, budget alert, usage after a week) · 🤖 integration **I6** (responsive/a11y) · the digest's first live send (needs SMTP). Run it locally: [`docs/run-locally.md`](docs/run-locally.md).

---

## v1 → v2 mapping (reference)

| v1 (Python) | What it does | v2 (Spring) equivalent |
|---|---|---|
| `app/main.py` | App startup, CORS, lifespan | `InsightshubApplication`, `@Configuration` classes |
| `app/core/config.py` | env vars via dotenv | `application.yml` + `@ConfigurationProperties` |
| `app/core/security.py` | JWT + Fernet encryption | Spring Security + `JwtEncoder`/`JwtDecoder`, AES-GCM (`javax.crypto`) |
| `app/db/postgres.py` | async SQLAlchemy engine | Spring Data JPA + HikariCP (auto-configured) |
| `app/db/redis.py` | redis client | `StringRedisTemplate` / Spring Cache |
| `app/models/user.py` | `UserInsights`, `UserTemplates` | `@Entity` classes + `JpaRepository` (redesigned in Phase 2) |
| `app/services/github_service.py` | GraphQL query to GitHub | `RestClient` + `.graphql` resource file |
| `app/services/insights_service.py` | stats + archetype computation | Plain `@Service` + Java records + streams |
| `app/services/ai_service.py` | Groq + Gemini fallback, persona | Spring AI `ChatClient`, prompt templates |
| `app/api/routes.py` | `/api/auth/*`, `/api/user/insights` | `@RestController`s + a `SyncService` |
| `fastapi-limiter` | rate limit per route | `HandlerInterceptor` + Redis (or Bucket4j) |

### v1 API contract (reference)
Keeping v1's response shape meant the v1 Next.js client could be pointed at v2. Since Part D, v2 has its own web UI and the contract lives in [`docs/api/contract.md`](docs/api/contract.md).
- `GET /api/auth/login`: redirects to GitHub (sets `oauth_state` cookie)
- `GET /api/auth/callback?code&state`: syncs data, redirects to `FRONTEND_URL/dashboard?token=<jwt>`
- `GET /api/auth/logout`: redirects to `FRONTEND_URL`
- `GET /api/user/insights`: `Authorization: Bearer <jwt>`, returns **snake_case** JSON:
  `user_name, archetype, stats{...}, display_json, weekly_review, craft_review, updated_at`

---

# Part A: Port v1

## Phase 0: Setup, infra & config ✅

**Concepts:** Spring Boot auto-configuration, starters, `application.yml`, profiles, externalized config.

**Hints**
1. ⚠️ **Version check first.** Your `pom.xml` uses Spring Boot **4.1.1** with Spring AI **1.0.0**. Spring AI 1.0.x was built against Boot 3.x (Spring Framework 6, Jackson 2). Boot 4 moved to Framework 7 and Jackson 3. Find out which Spring AI line supports Boot 4 and align the versions. Otherwise you'll hit confusing auto-config errors later.
2. The starters you added will try to connect to Postgres, Redis and OpenAI on startup. Make both reachable (local Docker or remote services).
3. Rename `application.properties` → `application.yml`. Read secrets from env vars with `${GITHUB_CLIENT_ID}` placeholders. Never commit secrets. Add a git-ignored `.env` or `application-local.yml`.
4. Create a typed config class: a Java `record` annotated with `@ConfigurationProperties(prefix = "insightshub")` holding `frontendUrl`, `backendUrl`, `jwtSecret`, `encryptionKey`, etc. Figure out how to enable it (`@ConfigurationPropertiesScan`).
5. Run `git init`. You'll want history for this.

**Done when**
- [x] Spring Boot / Spring AI versions are compatible
- [x] Postgres (Neon) + Redis (remote) reachable from the app
- [x] `./mvnw spring-boot:run` starts cleanly
- [x] Config values come from env vars through a typed `@ConfigurationProperties` record

---

## Phase 1: First endpoint: health ping ✅

**v1 reference:** `routes.py` → `GET /api/` (sets and gets a Redis key, runs `SELECT 1`)

**Concepts:** `@RestController`, `@GetMapping`, constructor injection, beans, `JdbcTemplate`, `StringRedisTemplate`, Actuator.

**Hints**
1. Unlike FastAPI's `Depends(get_db)`, Spring injects dependencies through the constructor. Use `final` fields + one constructor (or Lombok `@RequiredArgsConstructor`).
2. Return a `Map` or a `record` from the controller. Jackson serializes it for you.
3. Set a global `/api` prefix instead of repeating it on every controller (`server.servlet.context-path`, or a path prefix config). Think about which one also affects Actuator URLs.
4. Afterwards, look at `/actuator/health`. It already checks DB and Redis. That's the "Spring way" to do what v1's ping did.

**Done when**
- [x] `GET /ping` returned `{"redis":"pong","jdbc":1}`, then replaced by `/actuator/health` with details
- [x] You can explain what a bean is and who creates your controller

---

## Phase 2: Data model for v2 (JPA) 🟨

**v1 reference:** `models/user.py`, `db/postgres.py`. v2 needs more than v1's two tables, because habits need **history** (v1 overwrote `stats_json` on every sync) and rankings need **scores**.

**Concepts:** `@Entity`, `@Id`, `@OneToOne` + `@MapsId`, composite keys (`@EmbeddedId` / `@IdClass`), `@ManyToOne`, unique constraints, JSONB columns, `ddl-auto` vs Flyway migrations, `@Transactional`.

**Tables to design** (names are suggestions):
| Table | Purpose | Notes |
|---|---|---|
| `users` | GitHub id (PK), login, **email**, **encrypted GitHub token**, preferences (`alerts_enabled`, `no_commit_days`, `public_profile`), `last_synced_at` | Preferences could be a separate table. Your call. Part D adds `timezone`, `rest_days`, `chosen_archetype`, `archetype_changed_at`; `no_commit_days` is obsolete (hearts replace it) and stays unused |
| `insights` | Latest computed stats JSON, AI texts (year in review, weekly, craft), archetype | Replaces v1's `user_templates` |
| `daily_contributions` | `(user_id, date)` → `calendar_count`, `branch_commit_count` | Composite key. One row per day, backfilled from the contribution calendar (`V4__activity.sql`, Part D) |
| `commits` | `sha` (unique), user, repo, message, `committed_at` | **Deferred (Part D):** lazy-message detection becomes a Journal tip later |
| ~~`habit_alerts`~~ → **`hero_events`** | user, type (`LIFE_LOST`, `LIFE_GAINED`, `LEVEL_UP`, `LEVEL_DOWN`, `FAINTED`, `RESTED`, `CLASS_CHANGED`), `on_date`, details JSON, `digest_id` (null = not sent yet) | Unique `(user_id, type, on_date)`. Replaces `habit_alerts` (Part D, `V5__hero.sql`) |
| `digests` | user, sent-at, channel, status | Log of what was actually sent |
| `archetype_scores` | user, archetype, score, `algo_version`, computed-at | Used in Phase 13 |

**Hints**
1. ⚠️ **v1 is still live** (insight-hub-nu.vercel.app) on the same Neon DB. Don't let `ddl-auto: update` touch v1's `user_insights`/`user_templates`. Use new table names, or better, a separate Postgres **schema** for v2 (look at `spring.jpa.properties.hibernate.default_schema`).
2. GitHub IDs aren't generated by you, so no `@GeneratedValue` on `users`. Use `Long`.
3. For a table whose primary key is also a foreign key to `users`, like `insights`, look up `@MapsId`.
4. JSON columns: `@JdbcTypeCode(SqlTypes.JSON)` on a `Map<String,Object>` or on a record → `jsonb`.
5. With 7 tables, composite keys and unique constraints, this is the point where **Flyway** pays off (versioned SQL in `db/migration/V1__init.sql`, then `ddl-auto: validate`). Strongly recommended here, since it's what real teams do.
6. Timestamps: `Instant` for moments; `LocalDate` for "a day" in `daily_contributions`. Pick one timezone policy (UTC) and write it down.
7. Prefer `@ManyToOne(fetch = LAZY)` from child to `users`, and avoid `@OneToMany` collections on `User` unless you need them. Find out what the N+1 problem is.

**Done when**
- [ ] All tables exist in v2's own schema (via Flyway or entities), and v1's tables are untouched
- [x] Repositories exist and `findById` works for `users`
- [ ] Inserting the same `(user_id, date)` or commit `sha` twice is impossible at the DB level

---

## Phase 3: GitHub GraphQL client ✅

**v1 reference:** `services/github_service.py`

**Concepts:** `RestClient` (the synchronous HTTP client that replaced `RestTemplate`), resources on the classpath, Jackson deserialization into records, error handling, GraphQL variables and rate limits.

**Hints**
1. Put the query in `src/main/resources/graphql/user-insights.graphql` and load it with `@Value("classpath:...") Resource`.
2. Build one `RestClient` bean with `baseUrl("https://api.github.com")` using `RestClient.Builder` (auto-configured, so inject it).
3. Big design choice: map the response into **nested Java records** (type-safe, more code) or into a `JsonNode` (quick, like Python dicts). Recommendation: use records. That's where Java pays off. Use `@JsonIgnoreProperties(ignoreUnknown = true)`.
4. GraphQL returns HTTP 200 even on errors. Check the `errors` field yourself and throw a custom exception.
5. **Extend v1's query for v2:**
   - commit history nodes: add `oid` (the SHA) and `committedDate`, not just `message`
   - add a top-level `rateLimit { cost remaining resetAt }` and log it on every call
   - `contributionsCollection(from:, to:)` accepts a date range. The scheduled sync (Phase 10) can fetch only the recent days instead of the whole year
6. Rate limits: GraphQL gives **5,000 points per hour per user token**. Each user syncs with their own token, so the budget isn't shared between users.
7. Test it manually with a personal access token before wiring OAuth (e.g. a temporary dev endpoint or a `CommandLineRunner`).

**Done when**
- [x] Your GitHub profile is fetched and mapped into a record tree, including commit SHAs and dates
- [x] GraphQL errors raise a meaningful exception
- [x] `rateLimit.cost` / `remaining` show up in the logs

---

## Phase 4: Insights engine (pure Java + first tests) ✅

**v1 reference:** `services/insights_service.py` (`calculate_user_insights`, `determine_archetype`)

**Concepts:** Streams (`map`, `flatMap`, `sorted`, `Collectors.groupingBy`/`counting`), `LocalDate`/`DayOfWeek`, records, enums, **JUnit 5 + AssertJ** unit tests.

**Hints**
1. This is pure logic with no Spring needed. Keep it that way: input is the GitHub record from Phase 3, output is an `InsightsResult(Stats stats, Archetype archetype)` record.
2. Model the archetype as an `enum` with a display label (`"🕵️ The Bug Hunter"`).
3. Port v1's first-match archetype rules as they are for now. Phase 13 replaces them with scores, so keep the archetype logic in its own class, behind an interface, so it's easy to swap.
4. The "flatten weeks → days, sort by date, take last N" logic appears 3 times in v1. Write it once. You'll reuse it for `daily_contributions` too.
5. Language counting: `flatMap` over repos → languages, then `groupingBy(identity(), counting())`, then sort by value.
6. Write unit tests **here first**. There's no DB and no HTTP, so it's a perfect TDD target. Cover each archetype branch.
7. Note: the generated `InsightshubApplicationTests.contextLoads()` starts the full context, so it needs DB/Redis/Groq. Decide how to handle it (Phase 14: Testcontainers).

**Done when**
- [x] Output matches v1's `stats` JSON shape for the same input
- [x] Unit tests cover all 5 archetypes + heatmap edge cases (< 7 days)
- [x] **Review change:** archetypes redesigned as 8 scored D&D classes (see below)

**Archetype redesign (PR #3 review)**
- v1's five archetypes were first-match thresholds on raw totals, and some weren't habits at all. They're replaced by **8 classes**, each tied to one habit: 🛡️ Paladin (streaks), 🪓 Barbarian (huge PRs, bursts), 🧙 Wizard (languages), ⚙️ Artificer (infra files), ✨ Cleric (issues, fix commits), 🎻 Bard (PRs, audience, docs), 🧘 Monk (small PRs, descriptive messages), 🗡️ Rogue (weekends).
- `HabitProfile.from(user, days)` derives 15 signals (shares 0..1); commit signals use **only the user's own commits**. `ScoringArchetypeClassifier` gives every class a 0–100 score (weighted signals scaled between a floor and a cap); highest wins, ties go to declaration order, all-zero → Rogue. `ALGO_VERSION = 1`.
- Why code decides and not the LLM: stable classes for leaderboards, no prompt injection through READMEs or commit messages, works when the AI is down, unit-testable, no extra LLM call. The LLM writes the "character sheet" (title, lore) in Phase 5.
- `V2__archetype_classes.sql` swaps the CHECK constraint and maps old values (dry-run on Neon in a rolled-back transaction). Scores aren't persisted yet: that's Phase 7 / Phase 13's `archetype_scores` table.
- `InsightsEngineLiveTest` (opt-in) prints a real profile's signals and scores for calibration.

---

## Phase 5: AI layer with Spring AI ⭐ ✅

**v1 reference:** `services/ai_service.py`

**Concepts:** `ChatClient` fluent API, system vs user messages, `PromptTemplate` (StringTemplate `.st` files), OpenAI-compatible providers, multiple model beans + `@Qualifier`, structured output (`.entity(...)`), fallback strategies, parallelism with `CompletableFuture` / virtual threads.

**Hints**
1. **You don't need a Groq SDK.** Groq exposes an OpenAI-compatible API (already configured in Phase 0). Gemini also has an OpenAI-compatible endpoint, which is useful for the fallback.
2. Put `MANAGER_PERSONA` in `resources/prompts/persona.st` and each review prompt in its own `.st` file with `{variables}`. Load them as `Resource`s.
3. Build a `ChatClient` bean with `.defaultSystem(persona)` so every call carries the persona.
4. Fallback: you'll need **two** chat clients (primary + fallback). Auto-config gives you one, so read up on creating a second `OpenAiChatModel` manually and telling them apart with `@Qualifier`. Wrap the call in try/catch → fallback → static message, the same as v1. (Stretch: Spring Retry or Resilience4j.)
5. `generate_repo_summaries` returns a `Map<String,String>`. Try `.call().entity(new ParameterizedTypeReference<...>())` to get structured output in **one** call instead of 3.
6. v1 used `asyncio.gather` for 4 AI calls in parallel. In Java: `CompletableFuture.supplyAsync(..., executor)` + `allOf`. Enable virtual threads (`spring.threads.virtual.enabled=true`) and use a virtual-thread executor. Handle each future's exception separately (like `return_exceptions=True`).
7. Per-call options (`max_tokens`, model override for the cheaper 8B model) go through `.options(...)`.
8. Design the AI service so Phase 12 can reuse it. The digest email's intro will be written in the same mentor persona.
9. **Character sheet** (from the PR #3 review): a 5th generator that takes the scored class, `ArchetypeScores` and `HabitProfile` and writes a D&D-style title and short lore. The class is a **fixed input**; the LLM can't change it. Use structured output (`.entity(CharacterSheet.class)`) and a static fallback built from `Archetype.getLabel()` / `getHabit()`.

**Done when**
- [x] All 4 generators work against Groq (+ the character sheet), checked live by `AiLiveTest`
- [x] Killing the Groq key falls back to the next provider. Proven live with a dead key → a second Groq provider; Gemini is wired but switched off until `GEMINI_API_KEY` is set
- [x] The calls run in parallel: 5 generators in ~2 s live; unit test: 5 × 400 ms tasks finish in < 1.2 s

**As built**
- `ai` package: `LlmChain` (providers in order → first usable answer, never throws), `LlmProvider` (client + model per `ModelTier` + reasoning effort), `AiConfig` (Groq from auto-config + optional hand-built fallback `OpenAiChatModel`), `AiReviewer` (5 generators with static fallbacks), `AiInsightsService` (parallel on Boot's virtual-thread `applicationTaskExecutor`, per-task timeout and crash isolation), `AiProperties` (`insightshub.ai.*`).
- A provider chain instead of `@Qualifier` beans: the fallback is optional, and the try-next logic lives in one place instead of every generator.
- Repo summaries: one structured call (`.entity(RepoSummaries.class)`) instead of v1's 3. Invented repo names are dropped, skipped repos get v1's default text, and READMEs are marked as untrusted data in the prompt.
- Character sheet: a title that doesn't name the scored class is rejected (next provider, then the static sheet).
- **Models changed:** v1's Llama models are retired on Groq (B26). Now `openai/gpt-oss-120b` / `gpt-oss-20b` with `reasoning_effort: low` and token headroom (B27).
- **Persona changed (developer's call):** v1's roast and "tough love" manager are gone. v2 is a habit tracker, so every generator speaks as a supportive senior engineer: specific strengths, one habit to grow, one small next step, no sarcasm or invented praise. The roast became the **year in review**. `AiReviewerTest` fails if roast-style wording comes back into a prompt.
- Not done here (Phase 7): persisting the AI texts (the character sheet needs columns, and `insights.roast` gets renamed to `year_in_review`), and attaching summaries to `ranked_repos`.


---

## Phase 6: Auth: GitHub OAuth + JWT, stored token & email ✅

**v1 reference:** `routes.py` (`/auth/login`, `/auth/callback`, `/auth/logout`), `core/security.py`

**Concepts:** Spring Security filter chain, `SecurityFilterChain` bean, stateless sessions, OAuth2 authorization code flow, OAuth scopes, JWT (Nimbus: `JwtEncoder`/`JwtDecoder`), `@AuthenticationPrincipal`, symmetric encryption (AES-GCM).

**Hints**
1. You need new dependencies: `spring-boot-starter-security`, and `spring-boot-starter-oauth2-resource-server` for JWT validation (it brings Nimbus).
2. **Two approaches for the GitHub login part:**
   - **(a) Hand-rolled, like v1:** controller builds the authorize URL, stores `state` in a cookie, and the callback exchanges the code with `RestClient`. Easiest to reason about.
   - **(b) `spring-boot-starter-oauth2-client`:** Spring handles state/redirect/code exchange, and you plug in an `AuthenticationSuccessHandler` that issues your JWT.
   - Recommendation: start with **(a)** to understand the flow, then refactor to **(b)** as a learning exercise.
3. **Scopes change in v2.** Add `user:email` and fetch the primary, verified address with REST `GET /user/emails`. GraphQL only exposes the *public* email. Also think about `repo`: v1 asked for it, but it grants full read/write to private repos, and v2 now **stores** the token long-term. Public data only (no `repo`), or private activity too? Decide and write down the trade-off.
4. **Store the GitHub token encrypted.** The scheduled sync (Phase 10) runs without the user present, so it needs it. Fernet is Python-specific; use **AES-GCM** via `javax.crypto` (random 12-byte IV prepended, Base64 encoded, 32-byte key from `ENCRYPTION_KEY`). Never log it, and never return it from an API.
5. JWT issuing: define `JwtEncoder`/`JwtDecoder` beans using an HMAC secret (`NimbusJwtEncoder` + `ImmutableSecret`). Claims: `sub=githubId`, `name=login`, `exp=24h`.
6. **Backend-only:** there's no frontend to redirect to. Have the callback return the JWT as JSON (or a tiny HTML page showing it) for now, and keep the `FRONTEND_URL` redirect as something you can switch on later.
7. Protect `/user/**`, permit `/auth/**`, health and the unsubscribe link (Phase 12). Use `SessionCreationPolicy.STATELESS` and disable CSRF (Bearer tokens only).
8. Read the user in controllers with `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`.

**Done when**
- [x] The login round-trip yields a JWT (`AuthApiTest` with GitHub mocked, and live on 2026-10-14)
- [x] `users` has your email and an encrypted token that decrypts back to a working GitHub token (live: `GET /user` 200, scopes `read:user, user:email`)
- [x] `GET /api/user/insights` returns 401 without a token and 200 with one (404 until Phase 7 syncs the user)

---

## Phase 7: Sync orchestration & Redis caching ✅

**v1 reference:** the big body of `callback()` + `get_user_insights()` in `routes.py`

**Concepts:** Service layer design, `@Transactional` boundaries, Spring Cache abstraction (`@EnableCaching`, `@Cacheable`, `@CacheEvict`), `RedisCacheManager` config, TTLs, JSON serialization in Redis.

**Hints**
1. The v1 callback does far too much. Extract a `SyncService.sync(userId)`: fetch → compute → AI → upsert → evict cache. **Both** the login callback and the Phase 10 scheduler will call it, so it must not depend on anything from the HTTP request.
2. **Don't hold a DB transaction open during GitHub + AI calls** (they take seconds). Do the slow work first, then persist inside a short `@Transactional` method. Beware: `@Transactional` on a method called from *within the same class* doesn't work. Find out why (proxies!).
3. Build the insights response DTO as a record in an `InsightsQueryService.getInsights(userId)` annotated `@Cacheable(value="user_insights", key="#userId")`. Configure a 24h TTL in a `RedisCacheConfiguration` bean with a JSON serializer.
4. The migration for this phase adds the character-sheet columns and renames `insights.roast` to `year_in_review` (Phase 5 replaced the roast). Rename the `Insights` entity field to match.
5. `@CacheEvict(value="user_insights", key="#userId")` on the sync's persist step replaces v1's manual `redis.delete`.
6. v1 swallows sync errors so login still works. Keep that behavior, but log properly.
7. **Phase 5 follow-ups** (these calls start running for real users here):
   - The 90 s AI task timeout stops waiting but doesn't cancel the HTTP call (worst case about 2 × 30 s per provider). Cancel it, or make the timeouts add up to less than the task timeout.
   - Log token usage per call (`.call().chatResponse()` metadata instead of `.content()`), since Phase 10 syncs everyone daily.
   - Mark commit messages as untrusted data in `craft-review.st`, like READMEs already are.
   - Gemini fallback is untested against the real endpoint: needs `GEMINI_API_KEY` (and maybe `GEMINI_MODEL`).

**Done when**
- [x] Second `GET /user/insights` is served from Redis (`InsightsCacheTest`, and live on 2026-10-14: key `v2:user_insights::100276134`, TTL 24 h, plain snake_case JSON)
- [x] A fresh sync evicts the cache (`InsightsCacheTest`)

---

## Phase 8: Rate limiting ✅

**v1 reference:** `RateLimiter(times=5, seconds=60)` on auth routes, 20/60s on insights

**Concepts:** `HandlerInterceptor` / `OncePerRequestFilter`, `WebMvcConfigurer`, custom annotations + reflection, Redis `INCR` + `EXPIRE`.

**Hints**
1. Create a `@RateLimit(requests = 5, seconds = 60)` annotation for controller methods.
2. In a `HandlerInterceptor`, check `handler instanceof HandlerMethod`, read the annotation, and build a key `rl:{ip-or-userId}:{route}`. Use Redis `INCR`, and set `EXPIRE` on the first hit. If the count is over the limit, return 429.
3. Alternative: the Bucket4j library (token bucket). Build your own first, then compare.
4. `POST /api/user/sync` already has a per-user 10-minute cooldown (Phase 7, in the domain logic). What's left is the **shared** LLM quota: every sync makes 5 calls on the app's Groq key, so consider a global cap on syncs per minute.

**Done when**
- [x] The 6th login within 60s returns 429 with a `Retry-After` header (`RateLimitApiTest`, and live on 2026-10-14)

---

## Phase 9: Errors, validation, logging ✅

**Concepts:** `@RestControllerAdvice`, `ProblemDetail` (RFC 9457), custom exceptions, Bean Validation (`@Valid`, `@NotBlank`), SLF4J (`@Slf4j`), log levels per package.

**Hints**
1. Replace every `print(...)` from v1 with proper log levels. **Never log tokens** (v1 printed the GitHub token response!).
2. Map exceptions to responses in one place: `GitHubApiException` → 502, `UserNotFoundException` → 404, and so on.
3. Validate request bodies, such as the preferences update in Phase 12, with `@Valid` and constraint annotations.
4. CORS: the web UI calls `/api/*` through Next `rewrites` on its own origin, so no CORS is needed. The exception is `/sync` if the deploy host's proxy timeout is shorter than a sync (I4 decides); then CORS for that route only, through Spring Security's `.cors(...)`.

**Done when**
- [x] All errors return consistent `ProblemDetail` JSON
- [x] No secrets in logs

---

# Part B: New v2 features

## Phase 10: Scheduled daily sync & activity history ✅

**Goal:** every opted-in user gets synced once a day without logging in, and their activity is stored as history.

**Concepts:** `@EnableScheduling`, `@Scheduled(cron = ...)` with the cron expression in config, upserts (`INSERT ... ON CONFLICT`), virtual threads + bounded concurrency, error isolation per user, `java.time.Clock` injection.

**Hints**
1. Put the cron expression in `application.yml` (`insightshub.sync.cron`) and reference it with `${...}`, so you can run it every minute while developing.
2. The job: load users with a stored token → for each, call `SyncService.sync(userId)` → write `daily_contributions` and `commits`. **One user failing must not stop the others** (try/catch per user, then log and continue).
3. First sync per user: backfill a year of `daily_contributions` from the contribution calendar. Later syncs: only the recent window (`from:`/`to:`).
4. Upserts: Spring Data's `save()` does a SELECT and then an INSERT/UPDATE. For bulk daily rows, compare that with a native `INSERT ... ON CONFLICT DO UPDATE` query. Which one is better here, and why?
5. Run users in parallel on virtual threads, but limit concurrency (a `Semaphore`, or a fixed-size executor). Being polite to GitHub's secondary rate limits matters more than speed.
6. A 401 from GitHub means the token was revoked. Mark the user `needs_reauth` and stop syncing them, rather than failing every day.
7. Inject a `Clock` bean instead of calling `LocalDate.now()`. Your tests in Phase 11 will thank you.
8. Ask yourself: what happens if two instances of the app run the job at once? (Answer later with ShedLock, stretch goal.)
9. When the job finishes for a user, publish an event (`ApplicationEventPublisher`, e.g. `UserSyncedEvent`). Phase 11 listens for it.

**Done when**
- [ ] The job runs on schedule and fills `daily_contributions` for your user (`commits` deferred). Not run live yet: booting the app uses Neon
- [x] Re-running it creates no duplicate rows (`it/DailyContributionsRepositoryIT`)
- [x] A broken token for one user doesn't stop the others (`ScheduledSyncTest`)

**As built** (B1 #10, B36 fix #16/#17, B3 #20)
- `sync/ScheduledSync`: `@Scheduled(cron = "${insightshub.sync.cron}", zone = "UTC")`, default `0 0 3 * * *` (`"-"` turns it off, as the ITs do). Users with a token and no `needs_reauth` (`UsersRepository.findIdsForScheduledSync`), one virtual thread each, at most `insightshub.sync.concurrency` (4) at once via a `Semaphore`. Each user is isolated: failures are logged and counted, and a summary (synced / skipped / needs re-auth / failed) is logged per run. Two instances would both run it; the cooldown claim makes the second sync of a user a no-op (ShedLock is still the stretch answer to hint 8).
- ~~`SyncService.scheduledSync` claims the per-user cooldown like login and refresh, so a user who refreshed in the last 10 minutes is skipped.~~ Replaced on 2026-10-19 (#32), see below.
- **Branch scan** (`sync/BranchScanner`, `graphql/branch-activity.graphql`, `GitHubGraphQlClient.countBranchCommits`): for the last `branch-scan-days` (2: today and yesterday) that the calendar shows as 0, one call per day counts the user's commits on the 25 newest branches of their 20 most recently pushed repos (`author: {id}` = the GraphQL node id, now in `GitHubUser.id`). `upsertBranchCounts` writes `branch_commit_count` with `GREATEST`, so a count never goes down (commits stay counted after a squash merge deletes the branch) and `calendar_count` is left alone.
- **Answer: `history(since, until)` filters on the committer date**, not the author date. Verified live (`BranchActivityLiveTest`): a spring-boot commit authored 2026-04-30 and committed 2026-10-14 is found only around 14 Oct. So a rebase moves commits to the rebase day: rebasing your own old commits makes today active. The author filter still means only the user's own commits count.
- **Answer: calendar days are GitHub's own buckets, not UTC.** A commit counts on the day of its own timestamp's timezone (usually the user's); PRs and issues opened on the web count in the browser's timezone. So the calendar's dates are stored as the user's local dates, unconverted. The branch scan uses explicit local-midnight windows (`sync/DayWindow`, DST days 23 or 25 h), in UTC until B4 adds `users.timezone`.
- Cost: the scan is 1 GraphQL point per call, and runs only for empty days.
- Hint 9 (`UserSyncedEvent`) isn't built: B4/B5 decide how the hero engine hears about a sync.

**As built** (activity-only daily sync, #32, 2026-10-19, the developer's decision)
- The daily job makes **no LLM calls**. `ScheduledSync` calls `SyncService.refreshActivity(id, from)` (the same refresh the hourly hero job uses) with `from` = today (UTC) − `ActivityWriter.OVERLAP_DAYS` (7); `refreshActivity` adds a day of slack for zones and caps at a year. It writes the contribution calendar and the branch scan, nothing else.
- It no longer claims the 10-minute cooldown, so a refresh right after 03:00 isn't blocked; its writes are idempotent upserts (`GREATEST` for branch counts). `SyncService.scheduledSync` is gone; the run summary is `{synced, needsReauth, failed}`. `needs_reauth` is still flagged when GitHub rejects a token.
- AI texts and insights stats (Journal) are regenerated only by a sync the user starts (login, SAVE). Hero seeding stays on that full sync; every scheduled user has synced fully once.
- Why: each full sync is 5 LLM calls on the shared Groq key (4 × `gpt-oss-120b`, 1 × `gpt-oss-20b`; up to 10 with retries), so the daily job cost 5 calls per user per day for texts that barely change. Now the baseline is 0; LLM calls happen only on login and SAVE (10-min cooldown, `sync-global` 30 syncs/min), plus B7's planned digest intro.
- Tests: `ScheduledSyncTest` runs a real `SyncService` and checks the daily run never touches the AI, the engine, `InsightsWriter`, hero seeding or `claimSync`, and never makes the year-long fetch.

**As built** (fewer LLM calls, B11, `backend/b11-fewer-llm-calls`, the developer's decisions of 2026-11-05; replaces the activity-only rule above)
- **One combined call per user** (`AiReviewer.review`, `prompts/insights-texts.st`) on the primary tier writes all five texts as one JSON object (the schema from Spring AI's `BeanOutputConverter`). Each field is read and checked on its own, so a missing, empty, wrongly typed or overlong field falls back alone (`AiInsights.fallbacks`); a failed or timed-out call (`task-timeout` 90 s, now for the one call) falls back for every field. One user per prompt. The old five templates are gone.
- **When:** `SyncService.sync` (login, SAVE) calls it only for a user with no texts yet: a new user's first login, inline. Every later sync keeps the stored texts (`InsightsWriter.saveKeepingTexts`). Nothing calls the LLM at startup (`LlmStartupCheck`, `it/AiTextsIT`).
- **Daily job:** `ScheduledSync` now calls `SyncService.refreshInsights`: a full insights fetch (about 1 GitHub point), activity, stats and scores written, hero seeded, and the texts rewritten only when `ai/Regeneration` says the data moved since they were written: no texts, texts from before V7, a fallback, 7+ UTC days old, class changed, any class score ±5, commits/PRs/stars ±10% (from 0, any change), or the set of top repos (the 3 the summaries describe) or top languages changed. Compared with `insights.ai_inputs` (V7), the inputs stored with the texts, not yesterday's sync. At most once per user per UTC day. LLM calls capped at `insightshub.ai.regenerate.concurrency` (2). Summary `{users, regenerated, unchanged, fallbacks, needsReauth, failed}`. The hourly hero job keeps `refreshActivity` (no AI).
- V7: `insights.ai_generated_at`, `ai_inputs` JSONB, `ai_fallback`. `/api/user/insights` unchanged.
- Tests: `RegenerationTest` (every trigger, below every threshold, the 7-day rule in whole days, once a day), `LlmCallsTest` (1 call on a first login, 0 on later logins/SAVE and the hero refresh), `AiReviewerTest`, `AiInsightsServiceTest`, `ScheduledSyncTest` (real `SyncService`: unchanged → no call, moved → one, second run the same day → none, LLM cap), `InsightsWriterTest`, `AiPropertiesTest`, `it/AiTextsIT` (V7, JSONB round trip, start makes no call). Bug: B44.

---

## Phase 11: Hero engine (lives, XP, levels) ✅

**Goal:** evaluate each finished day once and move the user's hero: XP, streak, hearts and level. Replaces the original "Habit engine (3 rules)" (streak broken, no commits for N days, lazy messages); hearts cover the first two, and lazy messages become a Journal tip later. Full rules: [`docs/v2-hero-plan.md`](docs/v2-hero-plan.md) §2. Steps B4–B6.

**Concepts:** pure functions + exhaustive unit tests (tests first), `java.time` with IANA time zones, idempotent catch-up, hourly `@Scheduled` jobs, one transaction for state + events, unique constraints.

**The rules** (summary; the plan wins)
| Day was… | Effect |
|---|---|
| Active (`calendar_count > 0 OR branch_commit_count > 0`) | +10 XP · streak +1 · every 3rd consecutive active day: +1 heart (max 3) |
| A rest day with no activity | No effect, streak paused. Event `RESTED` |
| Inactive, not a rest day | −1 heart · streak → 0 · event `LIFE_LOST` |

- Hearts reach 0 → level −1 (minimum 1), XP back to the start of that level, hearts refill to 3 (`LEVEL_DOWN`, or `FAINTED` at level 1). Level *n* → *n+1* needs `50 × n` XP (`LEVEL_UP`).
- Each day is evaluated after it ends, with a 6-hour grace. Missed days are caught up in order, idempotent via `hero_state.last_evaluated_on`.
- First login seeds XP from the last 90 days' active days, without heart losses.
- Mood is derived, never stored. Class switching: score ≥ 40 or the top class, 7-day cooldown, level and hearts kept.

**Build**
1. `V5__hero.sql`: `users.timezone`, `rest_days` (at most 2 weekdays, CHECK), `chosen_archetype`, `archetype_changed_at`; `hero_state`; `hero_events` (replaces `habit_alerts`).
2. Pure `HeroRules.evaluateDay(state, date, activityCounts, isRestDay) → (newState, events)`, tests first (B4).
3. Hourly job for users past 06:00 local with `last_evaluated_on < yesterday`; `GET /api/user/hero`, `GET /api/user/hero/events`, `GET/PUT /api/user/preferences` (B5); `PUT /api/user/hero/class` (B6). Shapes in [`docs/api/contract.md`](docs/api/contract.md).

**Done when**
- [x] `HeroRules` covers every rule, edge case (heart cap, faint at level 1, rest days, month and timezone boundaries) and idempotency (B4: `HeroRulesTest`, `DueDaysTest`, `RestDaysTest`)
- [x] Running the hero job twice creates no duplicate events, and a 3-day outage is caught up in order (B5: `HeroServiceTest`, `it/HeroRepositoryIT`)
- [x] Class switch: unqualified → 409, during the cooldown → 429, success keeps the level (B6: `ClassSwitchTest`, `HeroClassServiceTest`, `HeroApiTest`, `it/HeroClassIT`)

**As built** (B4 #22)
- `hero/HeroRules`: pure static functions, no clock or Spring. `evaluateDay(state, day)` settles one day (a day already settled is a no-op, a skipped day is refused), `evaluateDays` catches up in order, `dueDays(lastEvaluatedOn, now, zone)` lists the days ready to settle (a day is due at 06:00 local the next morning, `GRACE` = 6 h), `seed(activeDates, lastDay)` builds the first-login hero. Records `HeroState`, `HeroDay`, `HeroEvent`, enum `HeroEventType`; `RestDays` converts the weekday bitmask.
- `V5__hero.sql`: `users.timezone` (default `UTC`), `rest_days` (bit 0 = Monday … bit 6 = Sunday, at most 2 set, CHECK), `chosen_archetype`, `archetype_changed_at`; `hero_state` (CHECKs: lives 1–3, `0 ≤ xp < 50 × level`); `hero_events` (`UNIQUE (user_id, type, on_date)`, `digest_id` for Phase 12).
- **Beyond the plan:**
  - `hero_days(user_id, on_date, xp_gained, streak)`, one row per settled day, for `xp_days` (events alone can't rebuild XP and streak).
  - `users.rest_days_previous` + `rest_days_from`, so "a change applies from the next day" still holds when the job catches up on days before the change.
- **Rule gaps B4 filled (⏳ developer to confirm):**
  - An inactive sign-up day costs nothing (only days *after* sign-up cost hearts); an active one counts.
  - The seed's streak is the run of active days ending yesterday (the plan only seeds XP).
  - "XP back to the start of that level" = XP 0 in the new level; a faint at level 1 also resets XP to 0 and refills the hearts.
- Event `details` and the `xp_days` rows are in `docs/api/contract.md` (`GET /hero/events`).

**As built** (B5 #25)
- `hero/HeroJob`: `@Scheduled(cron = "${insightshub.hero.cron}", zone = "UTC")`, default `0 5 * * * *` (5 past every hour). `HeroService.dueHeroes()` pre-filters in SQL (`last_evaluated_on` before the latest day due anywhere, i.e. UTC+18), then `HeroRules.dueDays` per user's zone.
- Per due user, on a virtual thread (at most `insightshub.hero.concurrency` = 4 at once, each user isolated, a run summary logged):
  1. `SyncService.refreshActivity(userId, firstDue)`: the contribution calendar from the day before the first due day, plus the branch scan in the user's zone. No AI, no cooldown. Settles only if the refresh worked (**B37**: the daily sync runs on a UTC clock, the evaluation on local clocks, so without it evening activity was missed).
  2. `HeroService.evaluate` settles every due day in one transaction (`HeroWriter.apply`): a compare-and-set on `hero_state.last_evaluated_on`, then `INSERT … ON CONFLICT DO NOTHING` for `hero_events` and `hero_days`. Re-runs, retries and a second instance write nothing twice.
- GitHub down: the days wait for the next hourly run and are caught up in order.
- **`needs_reauth` (✅ confirmed by the developer, 2026-10-19):** a user whose token GitHub rejects is **not** evaluated. The hero waits instead of losing a heart a day for activity we can't see, and catches up in order with real data after the next login.
- The hero is created at the end of every successful sync (`HeroService.seedIfMissing`, after the insights are saved), so `GET /hero` is 404 exactly until the first sync. A failed seed is logged and retried by the next sync.
- Days on or before the sign-up day (local) can't cost a heart; "before" can happen when a zone change moves the local dates back. Rest days use `RestDays.maskOn` per settled day. `PUT /preferences` stores today's mask as `rest_days_previous` and applies the new one from tomorrow, in the new zone.
- The sync's branch scan now uses `users.timezone` (UTC until B5).
- `insightshub.auth.redirect-to-frontend` is now `true`: the callback 302s to `${FRONTEND_URL}/dashboard#token=<jwt>` (`false` still returns JSON).
- API readings (approved by integration, in `docs/api/contract.md`): mood priority `FAINTED` > `LEVEL_UP` > `SLEEPING` > `HUNGRY` > base, `FAINTED` also after a `LEVEL_DOWN`; `SLEEPING` only on a rest day with no activity yet; the `/hero/events` window; `PUT /preferences` takes the full object; per-user rate limits `hero` (60/min) and `preferences` (20/min).
- Known limitation: the week strip shows days before sign-up as `missed` (no "none" status), so only a new user's first week.
- Follow-up idea: the refresh reuses `user-insights.graphql` with a short window; a calendar-only query would be cheaper.
- B6 contract agreed before it started (2026-10-19): see `PUT /hero/class` in `docs/api/contract.md`.

**As built** (B6)
- `PUT /api/user/hero/class` in `HeroController`, rate limit `hero-class` (10/min per user). Body `{archetype: CLASS|null}`; a missing key is `null` (follow the top score). 200 = the full updated hero, 409 not qualified, 429 + `Retry-After` in the cooldown, 404 before the first sync, Spring's own 400 for an unknown class.
- `hero/ClassSwitch.decide(...)`: pure, tests first. Order: the stored choice again → `Unchanged` (200, no event, cooldown not reset, even during the cooldown); not qualified → 409, checked **before** the cooldown, because waiting wouldn't help; cooldown → 429; else `Change`. Qualification reuses `HeroView.classView` (≥ `qualify-score` or the top class); `null` always qualifies. Pinning the class already followed (`null → scored`) is a change: `from == to`, and the cooldown starts.
- `CLASS_CHANGED` details `{from, to, chosen}` (`from`/`to` = `current` before and after, `chosen` = the stored value, may be `null`), `on_date` = the user's local today. `HeroEvent` now allows `null` detail values (it used `Map.copyOf`).
- Write: `HeroWriter.changeClass`, one transaction: `UsersRepository.changeClass` (JPQL compare-and-set on `archetype_changed_at`; `IS NULL` for a first change) + the event insert. A request that loses the race re-reads the user: if the winner stored the same class it's a 200 no-op, otherwise 429 with `Retry-After` from the winner's fresh `archetype_changed_at`.
- `HeroClassService` (`user/`) reads outside the transaction (open-in-view is off), then returns `HeroQueryService.hero`.

---

## Phase 12: Notifications: daily digest email ⬜

**Goal:** once a day, each user with unsent `hero_events` gets **one** email that summarises them in the mentor persona (Part D: cover `LIFE_LOST` at 1 heart left, `LEVEL_DOWN`, `FAINTED`, `LEVEL_UP`; no guilt trips).

**Concepts:** `JavaMailSender` + `MimeMessageHelper`, HTML email templates (Thymeleaf), Strategy pattern (`NotificationChannel`), delivery guarantees (at-least-once vs at-most-once), signed unsubscribe links, a preferences API.

**Hints**
1. `interface NotificationChannel { void send(User user, Digest digest); }` with an `EmailChannel` implementation. Discord comes later as a second implementation (stretch).
2. Digest job (`@Scheduled`, after the sync job): for each user with unsent `hero_events` (`digest_id IS NULL`) → build one digest → send → mark the events as sent. **No events, no email.**
3. Think through failure: if sending succeeds but marking fails, the user gets a duplicate tomorrow. If you mark first and sending fails, the alert is lost. Pick one and write down why (a digest that occasionally repeats is usually the lesser evil).
4. Content: Spring AI writes a short intro in the mentor persona from the alert list (reuse Phase 5). **The email must still go out if the AI call fails**, so have a plain template fallback.
5. HTML: add `spring-boot-starter-thymeleaf` and render `templates/email/digest.html` with `TemplateEngine` into a string. That's server-side rendering for email, not a web frontend.
6. SMTP for development: a sandbox inbox (e.g. Mailtrap) or a Gmail app password. Use a real provider (Resend, Brevo, SES) later. All of them are configured with `spring.mail.*` properties.
7. Unsubscribe: every email includes a link like `/api/notifications/unsubscribe?token=...`. The token should be signed (you already have JWT tooling) and the endpoint public.
8. Preferences API: `GET`/`PUT /api/user/preferences` (`timezone`, `rest_days`, `alerts_enabled`, `public_profile`) moves to B5 in Part D; the "N for no commits" preference is obsolete. Validate the input (Phase 9).

**Done when**
- [x] One digest arrives with all pending alerts, and none is sent when there's nothing to report
- [x] The unsubscribe link turns alerts off
- [x] An AI outage still produces an email

**As built** (B7)
- `notify/DigestJob`: `@Scheduled(cron = "${insightshub.digest.cron}", zone = "UTC")`, default `0 35 * * * *` (30 min after the hero job). Off unless `insightshub.digest.enabled` (`DIGEST_ENABLED`); enabled, it needs `spring.mail.host` (`MAIL_HOST`) and `insightshub.digest.from` (`DIGEST_FROM`) or startup fails. Off, the app boots without SMTP and logs "Digest disabled". Virtual threads, at most `concurrency` (4) users at once, each isolated; run summary `{sent, skipped, notDue, failed, aiCalls, aiFallbacks}`.
- `notify/DigestPlan` (pure, tests first):
  - **Due:** the user's local time is past `send-hour` (8), and no `SENT` digest yet on their local today. Yesterday settles by 06:00 local, so 08:00 sees it; events settled later that day wait for tomorrow (one email a day).
  - **Notable:** `LIFE_LOST` with `lives == 1`, `LEVEL_DOWN`, `FAINTED`, `LEVEL_UP`. Only these trigger an email, and the email then covers every fresh unsent event (the rest as "Also" lines).
  - **Skipped:** no notable event, alerts off, no email, or `needs_reauth` → the events are stamped with a `SKIPPED` digest and no email goes out, so nothing piles up (and turning alerts on later doesn't flood). Events more than 7 days old are always skipped.
- **At-least-once:** send, then one transaction (`DigestWriter`) inserts the digest rows and stamps exactly the mailed ids (`WHERE digest_id IS NULL`). A failed send writes nothing and is retried next hour; a failed stamp after a send repeats the email next run. A repeated email beats a lost "1 heart left".
- `V6__digests.sql`: `digests(id, user_id, status SENT|SKIPPED, channel, event_count, ai_intro, created_at)` with CHECKs (SENT ⇔ channel; an AI intro only on SENT), a partial index for the latest SENT per user, and the FK `hero_events.digest_id → digests.id`.
- **Content:** `DigestContent` (pure) writes one line per event, the subject (from the latest notable event) and three static intros (good / setback / mixed). `DigestIntro` asks the LLM for a 2-sentence intro (fast tier, `prompts/digest-intro.st`, persona as system prompt, `task-timeout`, answers over 600 chars rejected), else the static one. LLM cost: at most one call per mailed user per day, plus one per hourly retry while their email fails.
- **Email:** `NotificationChannel` (Strategy) → `EmailChannel`: Thymeleaf `templates/email/digest.html` (inline styles, Pixel Realm tokens) + a plain-text part; `List-Unsubscribe` + `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058). SMTP timeouts 10 s; mail health check off (an SMTP outage must not mark the app DOWN).
- **Unsubscribe:** `GET /api/notifications/unsubscribe?token=` shows a confirm page and changes nothing (mail scanners prefetch links); `POST` (the button, and the one-click target) sets `alerts_enabled = false`, idempotent. Public, HTML pages, a bad token is a 400 page, policy `unsubscribe` 10/min per IP. `security/UnsubscribeTokens`: HS256 with a key derived from `JWT_SECRET` (HMAC-SHA256 of a fixed label), `aud = unsubscribe`, 1 year, `sub` = GitHub id; a login JWT and an unsubscribe token can't stand in for each other (tested both ways). Rotating `JWT_SECRET` invalidates mailed links; the Settings toggle still works.
- Tests: `DigestPlanTest`, `DigestContentTest`, `UnsubscribeTokensTest`, `DigestServiceTest`, `DigestJobTest`, `DigestIntroTest`, `EmailChannelTest`, `UnsubscribeApiTest`; `it/DigestIT` (GreenMail in-process, the real AI layer offline → static intro, ids 9_000_6xx).

---

## Phase 13: Archetype scoring & leaderboards ⬜

**Goal:** archetypes become **scores**, and users are ranked within their archetype, globally.

> ✅ **Scoring is done** (PR #3 review): `HabitProfile` + `ScoringArchetypeClassifier` give 8 D&D classes 0–100 scores with `ALGO_VERSION`. Hint 1 is covered and hint 4 uses fixed caps. What's left here: storing scores (hints 2–3), leaderboards (5–7) and the seeder (8).

**Concepts:** normalisation and scoring design, versioned algorithms, SQL window functions (`RANK() OVER (PARTITION BY ...)`, supported in Hibernate 6+ HQL), Spring Data `Pageable`, Redis **sorted sets** (`ZADD`, `ZREVRANK`, `ZREVRANGE`), profiles for seed data.

**Hints**
1. ~~Replace the first-match rules (Phase 4) with a scorer~~ (done in #3): compute a 0–100 score for **each** archetype from normalised metrics (e.g. issues ratio → Bug Hunter, PR ratio → Open Source Hero, infra files → Architect, weekend share → Weekend Warrior). Primary archetype = highest score, with a fixed tie-break order.
2. Store the breakdown (which metric contributed what) as JSON, so you can explain a score, and maybe let the AI explain it.
3. Store `algo_version` with every score. When the formula changes, recompute everyone so old and new scores never mix in one ranking.
4. Normalisation: with few users, use fixed caps (e.g. 1,000 commits → 100). With many, percentiles. Start with caps.
5. Ranking in Postgres: `RANK() OVER (PARTITION BY archetype ORDER BY score DESC)`. Write it in HQL or as a native query. Endpoints: `GET /api/leaderboard?archetype=...&page=...` (paged), and `GET /api/user/rank` (your rank + percentile).
6. Mirror into Redis sorted sets (`leaderboard:{archetype}`): `ZADD` on recompute, `ZREVRANK` for "my rank", `ZREVRANGE ... WITHSCORES` for the top N. Postgres stays the source of truth, so rebuild Redis from it if it's empty.
7. Only users with `public_profile = true` appear on the leaderboard.
8. **Cold start:** you're the only user. Add a `dev`-profile seeder (`@Profile("dev")` + `CommandLineRunner`) that inserts fake users flagged `is_demo`, so rankings show something, and make sure it never runs in prod.

**Done when** (rewritten 2026-11-01 for the hero plan, which replaced score ranking: plan §3, Phase 13)
- [x] Every user has scores for all archetypes, and the primary archetype is derived from them (#3)
- [x] `GET /leaderboard` ranks heroes by level, then XP, within their current class, public profiles only, paged (B8, #42)
- [x] `GET /user/rank` gives the caller's rank and top percent, or the 404 "Not in the Arena" when they aren't ranked (B8, #42)
- [ ] The Arena tab shows the board and the caller's rank (ui; paused until the developer's new backdrop design)
- ~~Leaderboard + "my rank" from both Postgres and Redis~~, ~~changing `algo_version` triggers a full recompute~~: dropped. Ranks are computed per request in Postgres; class scores only qualify a class

**As built (B8, #42, to the contract of #40):** the plan wins over hints 5–6 here: the board ranks **heroes**, not class scores.
- `GET /api/leaderboard?archetype=&page=&size=` and `GET /api/user/rank` (JWT, policy `arena`, 30/min per user, shared). Shapes and the developer's decisions (2026-11-01) are in that day's HANDOFF REQUEST: `public_profile` stays opt-in (V1's `DEFAULT FALSE`), pages of 20 up to 50 (`arena/Arena`), and a caller who isn't on the board (private or `needs_reauth`) gets a 404 titled "Not in the Arena" (`NotInArenaException`), told apart from the "no hero yet" 404 by `title`.
- `repository/ArenaRepository`: one native query, `RANK() OVER (ORDER BY level DESC, xp DESC)` over the heroes whose user is public, not `needs_reauth` (the hero job skips them, so their hero would be frozen on the board), and whose **current** class `COALESCE(chosen_archetype, insights.archetype)` is the board's. Ties share a rank (1, 2, 2, 4) and are listed by GitHub id, so pages are stable. Computed per request: no stored rank, no Redis mirror (hint 6). The hourly hero job changes levels, the tables are small, and one source of truth can't disagree with itself. Revisit with a cache or sorted set if `EXPLAIN` gets slow at thousands of public heroes.
- No migration: no index helps much yet (the filter is on `users`, the sort on `hero_state`). The avatar is derived from the GitHub id (`avatars.githubusercontent.com/u/{id}`), so there's no new column.
- Not done: hint 8's `dev` seeder (the ITs seed their own boards), hints 2–3 (score breakdown, `algo_version` column).
- Tests: `ArenaTest`, `ArenaRepositoryTest`, `ArenaServiceTest` (the fixture board replayed through the service equals `leaderboard.json` / `rank.json`), `ArenaApiTest` (slice, **strict** against `leaderboard.json`, `rank.json`, `rank-not-in-arena.json`), `it/ArenaIT` (the window query on Postgres, ids 9_000_7xx), and `it/ApiContractIT` (both endpoints by shape, `size=51`/`page=-1` → field errors, a private hero → exactly `rank-not-in-arena.json`, no hero → `title` "Not Found" on both, both in the 401 sweep; ids 9_000_409–412). The code already matched the merged contract: no drift to fix.

---

# Part C: Ship it

## Phase 14: Testing, API docs & deployment ⬜

**Concepts:** `@SpringBootTest` + **Testcontainers** + `@ServiceConnection`, `@WebMvcTest` slice tests, `MockMvc`, security tests, OpenAPI docs, Docker images (`spring-boot:build-image`), production profile.

**Hints**
1. Add Testcontainers (Postgres + Redis) and fix `contextLoads()`. Mock the AI and GitHub clients in integration tests.
2. `@WebMvcTest` your controllers with mocked services, and test the security rules (401/200).
3. The web UI now exists, but the API docs still help the agents and the developer. Add `springdoc-openapi` (check which version supports Boot 4) for Swagger UI, and keep `.http` request files in the repo.
4. snake_case JSON for v1 compatibility: a global `SNAKE_CASE` naming strategy, or `@JsonProperty` per field. Know the trade-off.
5. Build an image with `./mvnw spring-boot:build-image`, add `application-prod.yml`, and deploy (v1 used Koyeb).

**Done when**
- [x] `./mvnw verify` is green with integration tests (I2: Testcontainers Postgres + Redis, CI on every PR; `DailyContributionsRepositoryIT` disabled until B36)
- [x] Backend contract tests (I3): `it/ApiContractIT` checks the real `/insights`, `/hero`, `/hero/events` and `/preferences` responses and their errors against `docs/api/fixtures/` and `contract.md`
- [x] Swagger UI documents every endpoint (B9, #45: `API_DOCS_ENABLED=true`, `OpenApiDocsTest` checks paths and schemas against the contract and fixtures)
- [ ] The app runs in production, and the daily sync + digest fire there (running since 2026-11-04: Vercel + the backend image on EC2, #51; the first live daily refresh and hourly hero job still to check; the digest waits on SMTP)

**As built (B10, `backend/b10-prod-profile`):** `application-prod.yml` (the image's `SPRING_PROFILES_ACTIVE=prod`). `server.forward-headers-strategy: native`: Tomcat's `RemoteIpValve` takes `X-Forwarded-For` only from internal addresses (Boot's default `internal-proxies`, which cover Caddy on `172.16.0.0/12`), so the IP-keyed limits see the visitor (`ForwardedClientIpTest` runs Boot's own Tomcat customizer on the profile). Hikari: 4 connections at most, `minimum-idle: 0`, `idle-timeout` 60 s, `keepalive-time` 0. Hikari 7's defaults (10 connections for good, a validation query every 2 min each) would keep Neon's compute from ever being quiet for the 5 minutes it needs to suspend; idle connections themselves don't hold it up. Jobs at concurrency 2; graceful shutdown awaits a running job 20 s, then requests 15 s, inside compose's 45 s. `ProdProfileTest` binds every value the way Boot does.

---

# Part D: Hero-pet plan (2026-10-16)

v2's purpose is now a **habit tracker with a Tamagotchi-style hero**, and the yearly "GitHub Wrapped" insights are secondary. The user's scored class character lives on a **Home** screen with **3 hearts**: a day with no GitHub activity costs a heart, and running out drops a level. Showing up earns **XP**, keeps a **streak** and wins hearts back. The yearly insights move to a **Journal** tab, and users can **change class** among the classes they qualify for. An earlier world-map UI was dropped because its levels meant nothing.

**Sources of truth**
- [`docs/v2-hero-plan.md`](docs/v2-hero-plan.md): product, game rules, backend and UI spec, visual spec. It wins over this file where they disagree.
- [`docs/api/contract.md`](docs/api/contract.md) + `docs/api/fixtures/`: the API shapes (owned by the integration agent).
- [`docs/design/`](docs/design/README.md): mockup PNGs, `tokens.json`, `sprites.json`, board sources.
- [`docs/agents/WORKFLOW.md`](docs/agents/WORKFLOW.md): lanes, branches, worktrees and the step order. [`docs/agents/HANDOFF.md`](docs/agents/HANDOFF.md): the agents' message board.

**Decisions (2026-10-16, don't relitigate)**
| Topic | Decision |
|---|---|
| What counts as active ("fed") | Any GitHub contribution on the contribution calendar **or** any commit authored by the user on **any branch** (branch scan, plan §3) |
| Forgiveness | **User-picked rest days**, at most 2 weekdays |
| Regaining hearts | **+1 heart per 3-day streak** (max 3) |
| Class switching | Any class with score ≥ 40, plus the top-scored class; **7-day cooldown**; level and hearts are kept |
| GitHub data | GraphQL for all activity (REST only where unavoidable: OAuth, `/user/emails`). Scopes stay `read:user user:email`, **no `repo`** |
| Visual style | Original pixel-fantasy art ("Pixel Realm" palette, plan §5). No Nintendo/Mario or Wizards of the Coast assets or names |

**What changes in the existing roadmap**
- **Frontend in scope:** a Next.js 16 (App Router) + React 19 + TypeScript + Tailwind 4 + TanStack Query app in **`web/`**, in this repo, so one PR can change the API and the UI together. Tabs: HOME · JOURNAL · HERO · ARENA · SETTINGS.
- **Phase 2.6 / 10:** `V4__activity.sql` creates `daily_contributions(user_id, date, calendar_count, branch_commit_count)`; the `commits` table is deferred. Phase 10 adds the **branch scan** (`branch-activity.graphql`, `countBranchCommits`) for days with `calendar_count = 0`.
- **Phase 11** is now **"Hero engine (lives, XP, levels)"**: `V5__hero.sql` (`hero_state`, `hero_events`, user timezone, rest days, chosen class) and the pure `HeroRules`.
- **`habit_alerts` → `hero_events`.** The Phase 12 digest reads unsent hero events. `users.no_commit_days` is obsolete (hearts replace "no commits for N days"); the column stays, unused.
- **Phase 13 → Arena:** rank by hero level, then XP, within the **chosen** class, for users with `public_profile`.
- **Auth hand-off:** the callback redirects to `FRONTEND_URL/dashboard#token=<jwt>` (a fragment, not `?token=`, so the JWT never reaches server logs or history).
- **Name** stays "Insight Hub" (a rename is deferred).

**Working agreement: three agents (replaces "the developer implements Phases 10–13")**

Three Claude Code agents build everything, each in its own git worktree and branch. The developer reviews and merges every PR. Agents never merge, never push to `main` and never force-push.

| Agent | Definition | Worktree | Owns | Steps |
|---|---|---|---|---|
| **backend** | `.claude/agents/backend-engineer.md` | `../ih-backend` | `src/main/**`, `src/test/**` (except `src/test/**/it/**`), migrations, `application.yml`, `pom.xml` main deps | B1 activity · B2 Phase 9 · B3 daily sync + branch scan · B4 `V5__hero.sql` + `HeroRules` · B5 hero job + `/hero`, `/hero/events`, `/preferences`, `#token` redirect · B6 `PUT /hero/class` · B7 Phase 12 digest · B8 Phase 13 Arena |
| **ui** | `.claude/agents/ui-engineer.md` | `../ih-ui` | `web/**` except `web/lib/api/types.ts` and `web/public/fixtures/**` | U1 scaffold · U2 title, `/dashboard`, tabs, system states · U3 Home · U4 Journal + Hero sheet · U5 Change class + Settings · U6 Arena |
| **integration** | `.claude/agents/integration-qa.md` | `../ih-integration` | `docs/**`, `PROGRESS.md`, `AGENTS.md`, `web/lib/api/types.ts`, `web/public/fixtures/**`, `e2e/**`, `.github/workflows/**`, `src/test/**/it/**`, `pom.xml` test deps | I0 docs · I1 shared types · I2 Testcontainers + CI · I3 contract tests · I4 wiring · I5 Playwright e2e · I6 responsive/a11y + release checklist |

- **One small PR per step** against `main`, then stop for review. Branches `backend/bN-…`, `ui/uN-…`, `int/iN-…`, each from fresh `main`; rebase before opening a PR.
- **I0 merges first.** Then backend and UI run in parallel (the UI works on fixtures and is never blocked by the backend); integration follows both.
- A change outside an agent's lane is a `REQUEST` in `HANDOFF.md`; the owner makes it. Contract, fixtures and shared types change together in one integration PR.
- AGENTS.md conventions still apply: no AI attribution, bugs in `BUGS.md`, no token-shaped literals, DTOs only, no secrets in logs.
- ⚠️ **B23:** until I2 lands, only targeted backend tests (`./mvnw test -Dtest=…`); never boot the app or run the full suite against Neon.

**Open questions (answered in B3 by backend, the proxy timeout in I4 by integration; record the answers here)**
- Does GitHub's GraphQL `history(since, until)` filter by author date or committer date? (A rebase rewrites the committer date.)
- Which timezone does GitHub use to bucket contribution-calendar days? If UTC, how do local days map onto it?
- Is the deploy host's proxy timeout long enough for a sync (worst case about 90 s)? If not, `/sync` goes direct with CORS for that route only.

---

## Stretch goals ⬜

- **S1. "Ask your Manager" (RAG chat):** embed READMEs, commit messages and repo summaries into pgvector during sync (`userId` metadata). `POST /user/ask` → `ChatClient` + `QuestionAnswerAdvisor` filtered by user. Needs an embedding provider (Groq has none), and re-enabling `spring.ai.model.embedding` and `spring.ai.vectorstore.type`.
- **S2. Discord alerts:** `DiscordWebhookChannel` as a second `NotificationChannel`. The user saves a webhook URL, and you POST JSON to it. Slack later.
- **S3. Friends leaderboard:** rank among the people you follow on GitHub (`following` in GraphQL).
- **S4. Observability:** Actuator + Micrometer metrics for AI calls, fallback rate, sync duration, digests sent.
- **S5. Multi-instance safety:** ShedLock so scheduled jobs run once across instances.

---

## Learning log

One entry per session: what was built, what was learned, open questions. Bug details live in [`BUGS.md`](BUGS.md) (B-numbers).

### 2026-09-27: Kickoff
- Scanned the v1 backend (`~/dev/insight_hub/server`) and the v2 skeleton; created this roadmap and the agent instructions file (now `AGENTS.md`, imported by `CLAUDE.md`).

### 2026-09-27: Phase 0 complete ✅
- Spring AI 2.0.0 on Boot 4 (B1). `.env` loaded with `spring.config.import` (B4). `@Validated` record `InsightsHubProperties` (B2, B3) fails fast on blank values. `.gitignore` covers secrets (B5).
- Groq as an OpenAI-compatible chat provider at `https://api.groq.com/openai/v1` (B8). Unused OpenAI models, embeddings and pgvector off via `spring.ai.model.*: none` / `spring.ai.vectorstore.type: none` (B6, B7).
- Decision: production Neon DB + remote Redis for dev (only the owner's data exists).
- Learned: starters switch features on, properties switch them off; unknown YAML keys are silently ignored; the real cause is at the first `WARN`/`Caused by`.

### 2026-10-01: Scope change: v2 features defined
- v2 = port + habit tracker + daily digest alerts + archetype ranking; backend-only; RAG is a stretch goal. See "Scope & decisions".
- Consequences for the port: Phase 3 fetches commit SHAs/dates and logs `rateLimit`; Phase 6 adds `user:email` and stores the GitHub token encrypted; Phase 7's `SyncService` must work without an HTTP request. v1 is still live on the same Neon DB, so v2 uses its own schema.

### 2026-10-01: Phase 1 complete ✅
- Built `/ping` (constructor injection, `StringRedisTemplate` + `JdbcTemplate`), then replaced it with `/actuator/health` + `show-details: always` (switch to `when-authorized` in Phase 6). Redis host fixed (B9).
- Learned: Actuator aggregates `HealthIndicator` beans from each starter; Redis connects lazily, Postgres eagerly.
- Carried to Phase 6: the `/api` prefix decision.

### 2026-10-01 → 10-06: Phase 2, steps 1–5
- Schema review: `users` (not `user`, a reserved word); `insights.user_id` as PK + FK (`@MapsId`); no stored `global_rank` (derive it in Phase 13); archetype as `varchar` + CHECK on `insights`; daily digest only.
- Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) owns the `v2` schema; `ddl-auto: validate`. `V1__users_and_insights.sql` applied to Neon; v1's `public` schema untouched.
- Archetype names are fixed by the CHECK constraint: `BUG_HUNTER, OPEN_SOURCE_HERO, SOFTWARE_ARCHITECT, WEEKEND_WARRIOR, CODE_CRUSADER` (replaced by 8 D&D classes in V2, PR #3).
- Entities `Users`, `Insights`, enum `Archetype`; `UsersRepository` (`findByAlertsEnabledTrue`, `@Query findDueForSync`), `InsightsRepository` (`findById` with `@EntityGraph`). Bugs along the way: B10–B18.
- Idempotent `dev`-profile round-trip runner (`DevDataRunner`) proved: `@MapsId`, `jsonb` round trip, enum vs CHECK, generated `createdAt`, fetch graph. `open-in-view: false`.
- Correction: `columnDefinition = "text"` is *not* required for `String` → `text`; Hibernate 7 accepts it (only documentation).
- Rules learned: DB defaults don't apply to explicit NULLs; `save()` persists or merges based on the id, so load existing rows before updating; never return entities from controllers (DTOs only).

### 2026-10-13: Working agreement changed
- **Port (phases 3–9):** implemented by the AI agent, one PR per phase for the developer to review.
- **Phase 2 step 6 and v2 features (phases 10–13):** implemented by the developer, with the AI agent reviewing and hinting.
- No AI-agent attribution in commits or PR descriptions. Bugs are logged in `BUGS.md`, and each PR lists the bugs found while building it.

### 2026-10-13: Phase 3 built (🤖)
- `github` package: `GitHubGraphQlClient` (`fetchUserInsights(token, login[, from, to])`), `GitHubClientConfig` (`RestClient` with base URL + `User-Agent`), typed records in `github.model`, `GitHubApiException` / `GitHubAuthException` (401, for Phase 10's `needs_reauth`). `Clock` bean in `config/ClockConfig`.
- Query `graphql/user-insights.graphql` = v1's query + `rateLimit`, commit `oid`/`committedDate`/`author.user.login`, and a `from`/`to` window (default: last 365 days; GitHub's max is 1 year).
- Tests: `GitHubGraphQlClientTest` (9 unit tests, `MockRestServiceServer`, fixed `Clock`); `GitHubGraphQlClientLiveTest` (opt-in, needs `GITHUB_TOKEN`). Live: the query costs **1 point**.
- Bug log: B19.
- ⚠️ For 2.6/11: `history` returns the default branch's last 5 commits by **anyone**. Filter by `author.user.login` (null when the commit email isn't linked to an account) to get the user's own commits.

### 2026-10-13: Phase 4 built (🤖)
- `insights` package: `InsightsEngine.calculate(GitHubUser)` → `InsightsResult(UserStats, Archetype)`. `UserStats` serializes with v1's snake_case keys (`@JsonNaming`). The archetype rules (`RuleBasedArchetypeClassifier`) sit behind `ArchetypeClassifier`, so Phase 13 can replace them.
- **Golden test against v1:** ran v1's actual Python `calculate_user_insights` on a fixture → `insights/v1-expected-insights.json`; `InsightsEngineParityTest` asserts the Java stats JSON is identical.
- Deliberate differences from v1: null-safe for empty repos (B20); the weekly heatmap always has 7 entries (v1 returned `[]` for an empty calendar).
- Tests: 1 parity + 13 engine + 14 archetype (all 5 archetypes, thresholds, priorities, infra-file signals).
- Bug log: B20 (v1 bug), B21, B22.

### 2026-10-13: PR #3 review: archetypes as D&D classes (🤖)
- Review: v1's archetypes weren't based on real habits, and the names should be fantasy (D&D) classes. Proposed and agreed: **code scores the class, the LLM adds the flavour** (Phase 5).
- 8 classes, `HabitProfile` (15 signals), `ScoringArchetypeClassifier` (0–100 per class, `ALGO_VERSION = 1`), `ArchetypeScores`; `InsightsResult(stats, habits, archetypeScores)`. `RuleBasedArchetypeClassifier` removed; the parity test now compares stats only.
- `V2__archetype_classes.sql`, so the developer's activity migration becomes `V3__activity.sql`.
- Calibration on tushar27x: Monk 52, Rogue 47, Paladin 44, Barbarian 44, Wizard 33, Artificer 33, Bard 10, Cleric 0.
- Tests: 32 habit-profile + 15 scoring + 14 engine + 1 parity, plus an opt-in live scoring test.
- Bug log: B23, B24.

### 2026-10-13: Phase 5 built (🤖)
- Spring AI 2.0's OpenAI module wraps the official `openai-java` SDK: a second model is built from `OpenAiChatOptions` (`baseUrl`, `apiKey`, `model`, `maxRetries`, `timeout`); `.options(...)` takes a builder. The primary is set to fail fast (1 retry, 30 s instead of 3 × 60 s) so the fallback gets its turn.
- StringTemplate renders a `List` with no separator (`[1, 2, 3]` → `123`), so lists are formatted in Java.
- Live: Groq had retired both v1 models (404). **v1 production is affected** (B26). Replacement gpt-oss models spent the whole `max_tokens` on reasoning until `reasoning_effort: low` + headroom (B27). The LLM gendered a user until the persona said not to (B28).
- Tests: 9 chain + 14 reviewer + 4 parallel-service (with a recording `FakeChatModel`, so the real `ChatClient` path runs), plus opt-in `AiLiveTest` (needs `GROQ_API_KEY`).
- Bug log: B25–B28.
- Developer's review: drop the roast. The habit tracker needs encouragement, so the persona became a supportive senior engineer and the roast became a year in review. Checked live: each review names real strengths and ends with one concrete step.
- PR #4 merged (`ce232bd`). Phase 5 follow-ups are listed in the Phase 7 hints.
- Open questions for the developer:
  - Archetype calibration: should Monk need small PRs as well as descriptive messages? Should zero activity stay Rogue? Recalibrate on public-only data (what users actually get, see Phase 7)?
  - v1 production still calls the retired Groq models (B26). Fix v1 (two model names in `ai_service.py`), or leave it until v2 replaces it?

### 2026-10-14: Phase 6 built (🤖)
- Approach (a), hand-rolled like v1, same paths: `GET /api/auth/login` (302 to GitHub, `oauth_state` cookie: HttpOnly, SameSite=Lax, 10 min, path `/api/auth`), `GET /api/auth/callback` (constant-time state check, code exchange, returns `{access_token, token_type, expires_at, login}` as JSON), `GET /api/auth/logout` (302 to `FRONTEND_URL`). `/api` prefix kept for v1's contract. `insightshub.auth.redirect-to-frontend: true` restores v1's `FRONTEND_URL/dashboard?token=...`.
- Scopes `read:user user:email` (decision above). The email comes from REST `GET /user/emails`: primary **and** verified, else null.
- `security` package: `TokenCipher` (AES-256-GCM, random 12-byte IV, Base64 `iv‖ciphertext‖tag`; the GitHub id is bound as associated data, so a ciphertext copied to another user's row fails to decrypt), `JwtConfig` (HS256 `NimbusJwtEncoder`/`NimbusJwtDecoder`, issuer checked, secret ≥ 32 bytes), `JwtService` (`sub`=GitHub id, `name`=login, `exp` 24 h), `SecurityConfig` (stateless, CSRF off, `/api/auth/**` + health + `/error` public, everything else needs a JWT).
- `AuthService.completeLogin`: GitHub calls first, then load-or-create the user (B18), update login/email/token, clear `needs_reauth`. No sync yet (Phase 7). `UserController`: `GET /api/user/insights` reads the stored row (v1 keys), 404 before the first sync.
- Health `show-details: when-authorized` (planned in Phase 1): anonymous callers only see `UP`/`DOWN`.
- Tests: 8 cipher + 4 JWT + 7 OAuth client + 4 service + 12 web/security slice (`@WebMvcTest` with the real filter chain; one test does the whole login → JWT → `/api/user/insights` round trip). Booted the real app: health 200, insights 401, login 302 with public scopes, bad state 400.
- Bug log: B29, B30 (v1 logs every user's GitHub token).
- PR #6 merged (`a4922cb`). GitGuardian flagged fake test keys (B31); fixed and history rewritten before merge.
- Live check passed (2026-10-14) with a dedicated OAuth App (callback `http://localhost:8080/api/auth/callback`; the first try failed with GitHub's "redirect_uri is not associated with this application" until the callback URL matched exactly). Stored token decrypts, `GET /user` returns 200 with the matching id, granted scopes are `read:user, user:email`, and decrypting with another owner id is rejected.

### 2026-10-14: Phase 7 built (🤖)
- `sync` package: `SyncService` (decrypt token → GraphQL → `InsightsEngine` → `AiInsightsService` → write; only needs a user id, so Phase 10 can call it), `InsightsWriter` (the short `@Transactional` + `@CacheEvict` write, in its own bean because a call from inside the same class skips Spring's proxy), `SyncProperties`.
- **Refresh every 10 minutes** (developer's review, replaces v1's 7-day rule): `POST /api/user/sync` and login both call `syncIfDue`, which first claims the cooldown with one atomic `UPDATE users SET sync_requested_at = now WHERE ... sync_requested_at < now - 10m` (`insightshub.sync.min-interval`). Double clicks can't start two syncs, and a failing sync still counts. Responses: 200 with fresh insights, 429 + `Retry-After` during the cooldown, 409 when GitHub revoked the token, 502 when GitHub is down. Like v1, a failed sync at login is logged and the login still succeeds. GitHub 401 → `needs_reauth` (Phase 10 skips those users).
- Read side: `InsightsQueryService.getInsights` with `@Cacheable("user_insights")`. Redis via `RedisCacheManagerBuilderCustomizer`: 24 h TTL, typed JSON serializer for the record (no Java class names), key prefix `v2:` (v1 shares this Redis), null values not cached, transaction-aware (evict after commit).
- `V3__insights_sync_columns.sql`: `roast` → `year_in_review`; new `repo_summaries`, `character_title`, `character_lore`, `archetype_scores` (for Phase 13), `algo_version`; `users.sync_requested_at` (cooldown). **The developer's activity migration becomes `V4__activity.sql`.**
- `GET /api/user/insights` response: v1 keys plus `archetype_scores`, `year_in_review`, `repo_summaries`, `character_sheet` (v1's `display_json` is gone with the roast).
- Phase 5 follow-ups: provider timeouts 20 s (2 attempts × 20 s × 2 providers = 80 s < 90 s task timeout; startup warning if the budget is exceeded); token usage logged per LLM call (`LlmChain.text`/`entity` keep the `ChatResponse`); commit messages marked as untrusted in `craft-review.st`. Gemini fallback still untested live (needs `GEMINI_API_KEY`).
- Tests: 8 sync + 3 writer + 3 cache (real `@Cacheable`/`@CacheEvict` through Spring proxies, in-memory cache) + 2 Redis config (JSON round trip, TTL, prefix) + 5 refresh endpoint + 2 login-sync + 1 token usage. The atomic `claimSync` query needs a real database to test (Phase 14, Testcontainers). Booted the app with Flyway off to check the wiring and the JPQL without touching Neon.
- Bug log: B32, B33.
- PR #7 merged (`81ed7c0`).
- **Live check passed (2026-10-14):** V3 applied by Flyway. Login → sync in 7.7 s (GitHub 3.8 s, 5 AI tasks in parallel 2.2 s, DB write 2.8 s: Neon is in us-east-2), about 3,600 LLM tokens per sync. Two `GET /api/user/insights`: 200 both, identical, the second from Redis (1.6 s → 0.7 s; both are remote). `POST /api/user/sync` during the cooldown: 429, `Retry-After: 451`.
- **Finding:** the live sync made tushar27x a **Wizard (83)**, Artificer 0; the Phase 4 calibration said Monk 52 (Wizard 33, Artificer 33). Calibration used a personal token that also sees 5 private repos; the OAuth token is public-only (scope decision), so the top repos differ. Expected from the scope trade-off, but it changes archetypes noticeably.

### 2026-10-14: Phase 8 built (🤖)
- `ratelimit` package: `@RateLimit(policy, key = IP | USER)` on controller methods; `RateLimitInterceptor` (a `HandlerInterceptor`, so it runs after Spring Security: bad JWTs get 401 before they count, and the user is known); `RedisRateLimiter` (fixed window: one Lua script does `INCR` + `PEXPIRE` atomically and re-adds a lost expiry); `RateLimitConfig` registers the interceptor and fails startup if an annotation names a policy missing from `insightshub.rate-limit.policies`.
- Limits (configurable): login 5/min per IP, callback 5/min per IP (v1), insights 20/min per user (v1), **all syncs 30/min together** (`sync-global`, protects the shared Groq key). Over the limit: 429, `Retry-After` (seconds, rounded up), `X-RateLimit-Limit`/`-Remaining`, `ProblemDetail` JSON (the Phase 9 format).
- Decisions (developer accepted the defaults): **fail open** when Redis is down (warning logged; Redis timeouts lowered from Lettuce's 60 s to 2 s so a dead Redis costs at most 2 s); client IP from the connection, **`X-Forwarded-For` not trusted** (spoofable; Phase 14 enables `server.forward-headers-strategy` behind a real proxy); 30 syncs/min is a placeholder until the Groq plan's limits are known.
- `SyncService.syncIfDue` now checks, cheapest first: the user's cooldown (read only, so cooldown spam can't drain the global cap), the global cap (`BUSY` → 429 on refresh; login still succeeds without a sync), then the atomic claim. It returns `SyncResult(outcome, retryAfter)`.
- Fixed window vs token bucket: a fixed window allows a burst at the boundary (5 at 0:59 + 5 at 1:01). Bucket4j's token bucket would smooth that; not worth a dependency at these limits.
- Tests: 6 Redis limiter (mocked Redis: decisions, key/window, fail open) + 6 interceptor slice (6th login → 429, per IP, `X-Forwarded-For` ignored, per user, bad token not counted, unannotated endpoints free) + 3 policy check + 5 sync gates. The Lua script ran live: 6th login → 429 `Retry-After: 60`, key `v2:rl:login:ip:0:0:0:0:0:0:0:1` with count 6 and a TTL.
- No new bugs.
- For Phase 9: the 429 body is a `ProblemDetail` without `instance`; the error handler there should produce the same shape for every error.
- PR #8 merged (`a51a427`).

### 2026-10-14: End of session
- Merged today: #5 (Phase 5 docs), #6 (Phase 6, auth), #7 (Phase 7, sync + cache), #8 (Phase 8, rate limiting). The port is done except Phase 9.
- Also on `main`: agent instructions made tool-agnostic (`AGENTS.md`, imported by `CLAUDE.md`); Neon agent skills `neon` and `neon-postgres` in `.claude/skills/` (Claude Code only), pinned in `skills-lock.json`.
- Live checks passed for Phases 6, 7 and 8 against Neon, Redis, GitHub and Groq.
- Open for the developer:
  - Phase 2.6: `V4__activity.sql` (needed by Phase 10).
  - Archetype calibration questions (Phase 5 log), now including "recalibrate on public-only data?" (Phase 7 finding).
  - v1 production: B26 (retired Groq models) and B30 (GitHub tokens in v1's logs).
  - GitGuardian: resolve the two B31 incidents as test credentials.
  - Groq plan limits, to replace the `sync-global: 30/min` placeholder.
- Next session: Phase 9 (errors as `ProblemDetail` everywhere, validation, logging; no secrets in logs).

### 2026-10-16: Hero-pet plan and three agents (Part D)
- New direction: a hero-pet habit tracker (3 hearts, XP, levels, streaks, rest days, class switching) with a Next.js web UI in `web/`. Spec in `docs/v2-hero-plan.md`, decisions in Part D.
- Working agreement changed: three Claude Code agents (backend, UI, integration) build everything, including Phases 10–13, in separate worktrees; the developer reviews and merges. Lanes and step order in `docs/agents/WORKFLOW.md`.
- I0 (integration): committed the kit (`docs/**`, `.claude/agents/**`) and updated this file and `AGENTS.md`. Phase 11 renamed "Hero engine (lives, XP, levels)"; `habit_alerts` → `hero_events`; `commits` table deferred; `users.no_commit_days` obsolete.
- Next: backend B1 and UI U1 in parallel, then integration I1/I2.

### 2026-10-16: B1 built, activity history (🤖, Phase 2.6 / 10)
- `V4__activity.sql`: `daily_contributions(user_id → users ON DELETE CASCADE, date, calendar_count, branch_commit_count, PRIMARY KEY (user_id, date))`, counts `NOT NULL DEFAULT 0` with `>= 0` checks. No JPA entity (`ddl-auto: validate` only checks entities); B4/B5 can add one if reads need it.
- `DailyContributionsRepository` (`JdbcTemplate`): one batched `INSERT … ON CONFLICT (user_id, date) DO UPDATE SET calendar_count = EXCLUDED.calendar_count`. It never touches `branch_commit_count` (the B3 branch scan owns it). Answer to the Phase 10 hint: `save()` does a SELECT per row; the native upsert writes a year in one batch and is idempotent on the key.
- `ActivityWriter` (separate `@Transactional` bean, like `InsightsWriter`): the first sync backfills every calendar day GitHub returns (about a year, zero days included, so a stored 0 means "checked, nothing"); later syncs rewrite from 7 days before the latest stored day onwards (`OVERLAP_DAYS`), which also fills gaps after missed syncs. GitHub still fetches the full year, because the insights need it.
- `SyncService.sync` writes the calendar right after the GraphQL fetch, before the slow AI step, from `InsightsEngine.sortedDays` (now public). A GitHub failure or 401 writes nothing.
- **Assumption (verified in B3):** dates are stored exactly as GitHub's calendar returns them; which timezone GitHub buckets them in is still open.
- Tests: 5 `ActivityWriterTest` (backfill, overlap window, gap across a month boundary, stored day ahead of the calendar, empty calendar) + 4 `DailyContributionsRepositoryTest` (SQL, parameter mapping, empty batch skipped, no rows → empty) + 2 new `SyncServiceTest` (sorted calendar written before AI; no user → no days). The real upsert against Postgres waits for Testcontainers (I2). App not booted (B23).
- No new bugs.

### 2026-10-16: B2 built, Phase 9 errors, validation, logging (🤖)
- One error format everywhere: `application/problem+json` with `{type, title, status, detail, instance}`. `type` is always `about:blank` (Spring 7 leaves it null, B34). `instance` is the request path, never the query string.
  - Controllers: `error/ApiExceptionHandler` (`ResponseEntityExceptionHandler` + `GitHubApiException` → 502 + catch-all 500 with a generic detail; exception text is logged, never returned).
  - Before controllers: 401/403 from `SecurityConfig` (keeps Spring's `WWW-Authenticate`, fixed detail), 429 from `RateLimitInterceptor`, both via `ProblemDetails.write`.
  - Container errors: `error/ProblemErrorController` replaces Boot's `BasicErrorController` on `/error`.
- Domain errors: `TooManyRequestsException` (429 + `Retry-After`, reused by B6's class cooldown). `POST /sync`'s cooldown/busy now returns a ProblemDetail body (it was an empty 429). GitHub failures during login are now 502 (were 500).
- Validation: 400 adds `errors: [{field, message}]`, with fields converted to snake_case (B35). The contract addition is proposed to integration. The callback's `code`/`state`/`error` are capped at 256 chars, and `error` must match `[a-z_]+` because it's echoed in the 400.
- Logging: audited every `log.*` call and every exception message; none carries a token or secret. `application.yml` pins `org.hibernate.orm.jdbc.bind`, `org.springframework.web.HttpLogging` (would log the OAuth form with the client secret), `org.springframework.web.client` and `org.springframework.security.oauth2` at WARN.
- For B5: Jackson 3 rejects `null`/missing values for primitive fields (`FAIL_ON_NULL_FOR_PRIMITIVES`) with "Failed to read request" before validation runs, so use boxed types in request DTOs to get field errors.
- Tests: 9 handler (standalone MockMvc, every error kind) + 3 `/error` + 3 `ProblemDetails` + 2 security writers + 10 new assertions/tests in `AuthApiTest` (problem bodies, callback validation, no echo of tokens) + `RateLimitApiTest` shape. App not booted (B23).
- Bug log: B34, B35.

### 2026-10-16: I2 Testcontainers + CI (🤖 integration)
- `src/test/**/it/`: `@IntegrationTest` (`@SpringBootTest` + `TestcontainersConfig`: Postgres 17 + Redis 7 via `@ServiceConnection`, Testcontainers 2.0.5 from the Boot BOM, test secrets generated at runtime per B31, LLM base URL on a closed local port). The old `contextLoads` is now `ApplicationContextIT`: Flyway migrates an empty DB, `ddl-auto: validate` passes and nothing lands in `public`. `UsersRepositoryIT` finally tests the atomic `claimSync` (left for Phase 14 in Phase 7).
- **B23 fixed:** Surefire and Failsafe set `spring.datasource.url` and the Redis host to unresolvable `*.invalid` hosts (system properties beat `application.yml` and `.env`; `@ServiceConnection` beats both). `./mvnw test` is now the full unit suite, offline; `./mvnw verify` adds the ITs and needs Docker.
- **B36 found:** B1's `DailyContributionsRepository` SQL is unqualified and only JPA knows about `v2`, so on a real Postgres every upsert fails with `relation "daily_contributions" does not exist`, and `SyncService.sync` fails at that step. The mocked unit tests couldn't see it. Proven fixable with `SET search_path TO v2` on the pool (all 3 ITs pass); the backend owns the fix. `DailyContributionsRepositoryIT` is `@Disabled("B36 …")` until then.
- CI (`.github/workflows/ci.yml`): backend `./mvnw verify`; web `design:check` (before `build`, which regenerates the design files), lint, test (incl. the I1 fixture test), build. No secrets.
- Locally: 215 unit tests pass (5 opt-in live tests skipped), 6 ITs (3 skipped for B36), about 18 s with images cached. Docker needed the developer in the `docker` group.
- Working agreement: `--force-with-lease` is now allowed on your own open PR branch after a rebase, after checking the remote has no commits you lack (WORKFLOW.md).

### 2026-10-16: B36 fixed, U3, closed-eye sprites, B3 (🤖 all lanes)
- **B36 fixed** (#16, #17): `DailyContributionsRepository` qualifies its table with the Flyway schema (`spring.flyway.default-schema`). Chosen over a pool `search_path` because Neon's pooler (PgBouncer, transaction mode) doesn't reliably keep session settings. All 6 ITs run in CI with none skipped.
- **U3** (#18, ui): the Home screen. The UI now imports `web/lib/api/types.ts`; `types.tmp.ts` is gone.
- **Closed-eye sprites** (#19, integration): the eyes sit on a different row per class, so `variants.sleepingEyesRow` (MONK, row 4) became `variants.sleepingEyes` (`{CLASS: {row, value}}`, 7 classes; PALADIN's visor already hides the eyes). The old key was removed after the UI switched. Lesson: a JSON shape with a `_doc` string next to the entries needs a cast through `unknown` in TS.
- **B3** (#20, backend): the daily sync and branch scan, see Phase 10 "As built". 255 unit tests, 9 ITs. The ITs turn the cron off (`insightshub.sync.cron: "-"`).
- #20 conflicted with #18 on `HANDOFF.md` (both appended at the bottom). Integration rebased the backend's branch at the developer's request and pushed with a lease pinned to the backend's last commit. Appending to `HANDOFF.md` from two lanes at once will keep conflicting: resolve by keeping both entries in merge order.

### 2026-10-17: B4 + U4, hero-events contract (🤖 all lanes)
- **B4** (#22, backend): V5 hero schema and the pure `HeroRules`; see Phase 11 "As built". 304 unit tests, 15 ITs. Three rule gaps filled, waiting for the developer's confirmation.
- **U4** (#23, ui): the Journal and the Hero sheet. Home's RECENTLY log skips 0-XP days; their `RESTED` / `LIFE_LOST` events tell them.
- **Contract (integration):** `xp_days` lists every settled day (0 XP on missed and rest days), and the event `details` are defined. `hero-events.json` is now a 30-day story replayed with B4's rules that ends exactly in `hero.json`'s state, with a fixture test for that. The old fixture had Monday and Tuesday at streak 1 and 2, which the rules can't produce with `hero.json`'s 2 hearts (the streak carries over the Sunday rest day), so mock-mode Home now shows "Streak 12 / Streak 11" where the boards show 2 / 1.
- **IT user ids:** each IT class owns a block of 100 GitHub ids (`IntegrationTest` javadoc). B4's first id collided with `UsersRepositoryIT`'s `USER + 1`.

### 2026-10-19: B5, B6 contract (🤖 all lanes)
- **B5** (#25, backend): the hourly hero job, `GET /hero`, `GET /hero/events`, `GET/PUT /preferences` and the `#token` fragment redirect; see Phase 11 "As built". 393 unit tests, 15 ITs. **B37** found and fixed before it shipped (stale activity at settle time).
- **Developer decision:** the hero job skips `needs_reauth` users (the hero waits, then catches up).
- **Integration** (`int/b5-contract`): the B5 readings and the B6 contract in `contract.md`, typed event `details` in `web/lib/api/types.ts`, `it/HeroRepositoryIT` (the job's writes on a real Postgres, block 9_000_3xx).
- Lesson: a strict replay test on the backend (`HeroQueryServiceTest` turns the fixture story into rows and expects the exact JSON) means a fixture event can't be added without the backend's test data following it. `CLASS_CHANGED` therefore isn't in `hero-events.json` until B6 adds it on both sides.
- Go-aheads given: B6 (backend), U5 (ui, continuing from uncommitted work), this PR then I3 (integration).

### 2026-10-19: U5 merged, I3 contract tests (🤖 integration)
- **U5** (#27, ui) merged after integration rebased it over #26 (only `HANDOFF.md` conflicted, both entries kept in merge order).
- **I3** (`int/i3-contract-tests`): `it/ApiContractIT` + `it/ContractShape`. The `@WebMvcTest` slices compare values strictly with mocked services. This IT goes through the whole stack (security chain, Redis rate limiter and cache, services, Postgres SQL and JSONB, Jackson) and checks each response against its fixture **by shape**: the same keys at every level, the same JSON kinds (integer vs decimal too), nullable only where the contract says `X|null`, plus the contract's enums and date/Instant formats. Also the error shapes: 404 before the first sync, 401 without a token, the validation 400s (`errors[]` with snake_case fields), and the `X-RateLimit-*` headers.
- The hero runs a 14-day story on the real tables, built so the rules emit every event type B5 can produce (`LIFE_LOST`, `LIFE_GAINED`, `LEVEL_UP`, `RESTED`, `LEVEL_DOWN`, `FAINTED`), and each `details` is checked against the contract's table. `CLASS_CHANGED` joins when B6 lands.
- `/insights` is seeded through the sync's real write path minus GitHub: the v1 parity user → `InsightsEngine` → the AI layer (static fallbacks) → `InsightsWriter`.
- Finding (no bug): `GET /hero` is 404 until the insights exist too, not just the hero (`HeroQueryService` needs the scores for the class block). The first sync writes both, so the contract holds.
- Lessons: the IT builds MockMvc from the shared context (`springSecurity()`), because `@AutoConfigureMockMvc` would make a second context with its own containers. Mutation-check a shape test: a removed enum value only fails if the data has it (on a Monday the week strip has no past days).
- Local Docker: this agent's shell lacked the `docker` group (added after the session started); `newgrp docker` works around it.

### 2026-10-19: U6, I3 merged; I4 wiring (🤖 integration)
- **Merged:** U6 (#28, the Arena "under construction" screen), I3 (#29) and B6 (#30, `PUT /hero/class`: Phase 11 is done).
- **I4** (`int/i4-wiring`):
  - `web/next.config.ts`: a rewrite `/api/:path*` → `${BACKEND_URL}/api/:path*` (server-side, default `http://localhost:8080`), so no CORS. The login link still goes straight to the backend (the `oauth_state` cookie lives there).
  - `experimental.proxyTimeout: 120_000`. **Finding:** Next 16's rewrite proxy times out after **30 s** by default and then answers a plain-text 500, while the backend keeps syncing and claims the 10-minute cooldown. A first-login sync (up to about 90 s) would have shown "road blocked", then a 429 on RETRY.
  - `e2e/wiring/proxy-check.mjs` (in CI): a fake backend + `next dev`. Path, query, `Authorization`, the PUT body, a 429's `Retry-After` and problem+json all pass through, and a 35 s `/sync` returns 200. Mutation-checked: without `proxyTimeout` it fails with a 500 after exactly 30 s.
  - Checked, no change needed: the callback strips a trailing slash from `FRONTEND_URL`; the `#token` hand-off uses `router.replace`, so the token leaves the address bar and history; per-IP rate limits are only on the direct `/auth/*` calls, so the proxy's single IP doesn't matter.
  - Contract: the wiring conventions, B6's order of checks (409 wins over the cooldown), its `detail` texts and the race no-op, and the browser-zone rule (only while the stored zone is the default `UTC`; a REQUEST to the UI).
  - [`docs/run-locally.md`](docs/run-locally.md): mock mode, and the full stack with its warnings (live Neon, Groq, the hourly hero job).
- Open for the developer: `.claude/agents/backend-engineer.md` lets the backend append "as built" notes under its phase in `PROGRESS.md`, while `WORKFLOW.md` puts `PROGRESS.md` in the integration lane. Pick one.

### 2026-10-19: End of session
- **Merged today:** #24 (B4 contract), #25 (B5: hourly hero job, `/hero`, `/hero/events`, `/preferences`, `#token` redirect), #26 (B5 readings + B6 contract), #27 (U5 Change class + Settings), #28 (U6 Arena shell), #29 (I3 contract tests), #30 (B6 class change: **Phase 11 done**), #31 (I4 wiring). Committed straight to `main`: `0ef3d35` (developer feedback, below). Open: #32 (activity-only daily sync, ready) and this docs PR (merge after #32).
- **Steps:** backend B1–B6 · ui U1–U6 · integration I0–I4, so 17 of 21. Left: B7 (digest), B8 (Arena), the real Arena board (after B8), I5 (e2e), I6 (responsive/a11y + release checklist), and deployment (host not chosen).
- **First full-stack run by the developer** (from `main`, live Neon/Redis/GitHub/Groq, via `docs/run-locally.md`). Feedback, built by the UI agent live in the main checkout and committed to `main` after approval, without a PR: pixel icons in the tab bar (`web/lib/tab-icons.ts`; the mockups only had squares), and an "i" button per class row on the Hero sheet showing what earns each class (weights and ranges from `ScoringArchetypeClassifier`, algo v1, in `web/lib/classes.ts` `CLASS_SCORING`).
- **Decisions by the developer today:**
  - The hero job skips `needs_reauth` users (the hero waits, then catches up).
  - The daily sync is activity-only, no LLM calls (#32).
  - Approved UI feedback may be edited live in the main checkout and committed straight to `main` (no branch or PR), as done for `0ef3d35`.
- **Questions the developer asked, answered from the code** (for the record):
  - XP: +10 per active day (any calendar contribution or a branch commit; volume doesn't matter), level n → n+1 needs 50 × n, XP restarts at 0 in each level; first login seeds 10 XP per active day of the last 90.
  - LLM calls: 5 per full sync (year/weekly/craft reviews and the character sheet on `openai/gpt-oss-120b`, one batched repo-summaries call on `openai/gpt-oss-20b`, all on **Groq**; up to 10 with retries). The provider chain is Groq, then Gemini, which is dormant without `GEMINI_API_KEY`. Every task has a static fallback.
- **Open for the developer:**
  - Pick one rule for `PROGRESS.md` "as built" notes: the backend appends under its phase (`.claude/agents/backend-engineer.md`) or sends a REQUEST (`WORKFLOW.md`). And whether `WORKFLOW.md` should record the feedback-to-`main` way of working.
  - B4's three filled rule gaps (Phase 11 "As built", ⏳).
  - Groq plan limits, to replace the `sync-global: 30/min` placeholder.
  - Deployment host; the first live run of the daily refresh and the hourly hero job.
  - Older items from 2026-10-14 (archetype calibration, v1 B26/B30, GitGuardian).
- **Tooling:** the agents' shells lacked the `docker` group (added after they started); `echo './mvnw verify' | newgrp docker` works, and a fresh login shell fixes it.
- **Next session:** go-aheads for UI's REQUEST (the browser zone, real class change), B7, and I5. Integration may also move the tab icons into `docs/design/sprites.json`.

### 2026-10-29: I5 Playwright e2e (🤖 integration)
- **Go-aheads (developer):** B7, the UI's REQUEST (browser zone, mock order, class PUT to the backend) and I5. Merged: #34 (browser zone on login, mock 409 before 429, class PUT to the backend) and #35 (B7 digest). Open: the I5 PR (#36).
- **I5** (`int/i5-e2e`), `e2e/` (its own `package.json`, Playwright 1.64, Chromium), in CI as the `E2E (Playwright)` job:
  - `mock` (25 tests): mock mode on its **production** build. Title → PRESS START, the five tabs (desktop nav and phone tab bar), Change class, a mock class change, SAVE's overlay, every `hero-variants.json` entry (all 7 moods and 4 tiers, asserted through Home's screen-reader summary and the tier label), the class cooldown, and `?mock_error=401|404|429` on the shell's GETs.
  - `stubbed` (22): the real build, `/api/*` answered by Playwright from `docs/api/fixtures/`. The `#token` hand-off (stored, gone from the URL and history, in no request URL, sent only as `Authorization`), reload, no session → title, an expired token. The browser zone (UTC replaced by one PUT with only `timezone` changed and the hero re-read after the PUT; a picked zone kept; a failed PUT ignored). SAVE: 429 + `Retry-After` (countdown, survives a reload), 409 reconnect, 502 + RETRY, network error, 401, 404. The shell's GETs: 404 → ROLL CHARACTER, a 502 that outlasts the automatic retry, 401. Change class: 200 (request body), 409, 429, 502. Settings: 200 (full object), 400 `errors[]`, 429.
  - `fullstack` (6): the real build against `it/E2eBackend` (the app on Testcontainers via `spring-boot:test-run`; `it/GitHubStub` answers every `github.com`/`api.github.com` call in-process; the LLM port is closed, so the AI texts are the static fallbacks). The real OAuth round trip (Playwright stubs only GitHub's authorize redirect and blocks any other request to GitHub), the first sync and the hero seed, the browser zone stored, Journal and Hero sheet, SAVE → a real 429 from the login sync's cooldown, Settings persisted across a reload, problem+json and 401 through the rewrite.
  - `TestcontainersConfig`: `--e2e.frontend-url` / `--e2e.backend-url` move the two URLs for the e2e backend, and the Gemini fallback key is forced empty, so a `.env` with `GEMINI_API_KEY` can't make the ITs call Gemini.
- **Findings:** B38 (`?mock_error` does nothing under `next dev`: StrictMode's double mount spends it on a cancelled fetch; the mock suite runs on a production build). B39 (`.git/info/exclude` hides new `prompts/` files and every `README.md`: #35's red CI, and `docs/design/README.md` + `web/README.md` were never committed; I5 adds the design README).
- **Lessons:** Playwright doesn't route a redirect's target: the first run reached the real GitHub sign-in page. Intercept the request that issues the redirect and block the host as a safety net. A one-shot 5xx on a GET never shows: the query client retries it once (4xx never). The stubbed zone test asserts the hero is read after the PUT completes (a slow PUT), not just that two GETs happened.
- **Merge order:** I5's first CI run failed 7 tests (4 change-class, 3 zone) because #34 wasn't in `main` yet: `PUT /hero/class` was still served by the in-browser mock, and nothing sent the zone. Rebased on #34 + #35: all green.
- **Open for the developer:** anchor or drop the two `.git/info/exclude` lines (B39).

### 2026-11-02 / 03: hi-res design, U7, B8 (🤖 integration)
- **Merged:** #40 (B8 contract), #41 (hi-res design import: `docs/design/art/` with 39 named PNGs, the 15 board sources, the id → file map), #42 (B8 / Phase 13: `GET /api/leaderboard`, `GET /api/user/rank`), #43 (U7: the PNG sprite sheets, every class in every mood, PNG backdrops, hearts, nav icons, portraits).
- **First look by the developer** (full stack from `main`, live Neon/Redis/GitHub/Groq): login and sync fine (9 s, scored WIZARD). Heroes and portraits were blank: `next dev`'s cache in `web/.next` predated #43 and served CSS without the sprite rules; a fresh cache fixed it (`docs/run-locally.md`, "Good to know"). Next 16 also logs an "instant navigation" validation warning per route in dev: harmless, worth `export const instant = false` on the client-rendered game pages when the UI resumes. Overnight the laptop slept and the backend lost its Neon and Redis connections (health DOWN); a restart fixes it.
- **Decisions by the developer:** not satisfied with the design; a **new backdrop design** comes next session and the UI resumes from it (Arena board included). Meanwhile: backend **B9** API docs (Swagger), and turning the digest email on live (needs SMTP settings), plus this close-out.
- **Fixed in docs:** the design README's tier ranges (keep 10–14, citadel 15+, as in `tokens.json`); I had written 10–19 / 20+.
- **Lessons:** a merge that renames shared CSS can leave a running developer's dev cache stale even though CI (always cold) passes. Two docs PRs touching `HANDOFF.md` at the same time conflict on the appended entries: keep both, and check the `Status:` line didn't get shared.

### 2026-11-04: ship as is, Arena shelved, U10 (🤖 integration)
- **Merged since the last entry:** #44 (B8/U7 close-out), #45 (B9: OpenAPI + Swagger UI, off by default; B40, B41), #46 + #47 (redrawn backdrops, U8), #48 (U9: Home in one column on big screens, quest wording instead of feeding).
- **Decisions by the developer:** ship the project in its current state. The **Arena board is shelved**: the tab, the `/arena` route and the "Show me in the Arena" switch go from `web/`. The backend's `/api/leaderboard` and `/api/user/rank` stay, unused, and the PUT still sends `public_profile` unchanged. Polish Hero and Settings, with everything centred.
- **U10** (#49, ui): one shared `Screen` width (`wide` 1132 / `narrow` 760 / `form` 640 px), centred under the header. Hero: character card and CLASS SCORES side by side at equal height, name/score/tagline centred under the portrait, the lore in its own card below. Settings: a centred 640 px column, LOG OUT full width, even 24 px gaps. Journal's tabs and reviews centred. B42: the top score row's empty bar cells matched its highlight colour.
- **Integration:** the mock e2e spec expects four tabs, `/arena` → 404 and no Arena switch; it rides in #49 with the UI change (split across two PRs, each PR's E2E job failed against the other half). All three suites pass with it (mock 25, stubbed 22, full stack 6). This docs PR (#50): Docs: the Arena marked shelved here, in `AGENTS.md` and the plan; the Swagger box ticked; the Swagger URL in `docs/run-locally.md`; the plan's "fed" wording replaced with U9's quest wording.
- **Lesson:** a UI change that removes something the e2e specs assert must ship with its spec change in the same PR, or neither PR goes green.
- **Still open for the developer:** I6 and the deployment host; SMTP for the digest; checking the hero's catch-up from 2026-10-18 in Neon (expected LV 5 → LV 2, 1 heart); the older items in the 2026-10-19 entry. `docs/design/png/` still shows the old art and layout.

### 2026-11-04: deployment plan (🤖 integration)
- **Decided by the developer:** Vercel for `web/`, an **AWS EC2 t3.micro** (1 GB RAM + 2 GB swap, 20 GB) for the backend only, Neon and Upstash free tiers as now. Oracle's free tier didn't work out.
- **Developer's review of #51:** CI pushes a **Docker image** to Docker Hub; the instance only pulls and restarts it. Region **`ap-south-1`** (Mumbai).
- **Added:** `docs/deploy.md` (instance, secrets, Docker Hub + GitHub settings, OAuth app, Vercel, memory, free-tier limits, release checklist), the root `Dockerfile` + `.dockerignore`, `docs/deploy/` (`compose.yaml` with Caddy, `Caddyfile`, `deploy.sh`: pull, restart, health check, roll back to `:previous`), the `image` job in `ci.yml` (build on every run, push on `main`). The first draft's systemd unit and SSH deploy workflow were dropped.
- **Measured:** the backend under the image's JVM flags peaked at 328 MB RSS through the full-stack e2e suite; the image boots with the `prod` profile in 4.9 s at 411 MiB (`docker stats`), as a non-root user.
- **Flagged:** the instance in Mumbai and Neon in Ohio are ~200 ms apart, and each request makes several database round trips. The developer's call: measure first, or move v2 to a Neon project in Asia (`docs/deploy.md`, "Region").
- **Found:** the IP-keyed rate limits would see every visitor as `127.0.0.1` behind Caddy; REQUEST to backend for the `prod` profile (trusted forwarded headers from loopback only, a small Hikari pool so Neon can suspend, job concurrency 2). Vercel's external rewrites wait 120 s, enough for the first sync.
- **Open for the developer:** the domain (or DuckDNS), the Docker Hub account and token, the Neon region question, SMTP.

### 2026-11-04: End of session (🤖 integration)
- **Merged today:** #49 (U10: Hero and Settings centred, Arena removed from `web/`, B42), #50 (docs for U10, B9, quest wording), #51 (deployment: `Dockerfile`, the CI `image` job, `docs/deploy/`, `docs/deploy.md`).
- **Decisions by the developer:**
  - Ship the app in its current state; the **Arena board is shelved** (the backend keeps `/api/leaderboard`, `/api/user/rank` and `public_profile`).
  - Hosting: **Vercel** for `web/`; the backend as a **Docker image** on Docker Hub (`tushar2703/insight-hub`), pulled by an **AWS EC2 t3.micro in `ap-south-1`** (Ubuntu, 1 GB + 2 GB swap); Neon and Upstash free tiers as before. Oracle's free tier didn't work out for the developer. CI pushes `:latest` + `:sha-<7>` after every green run on `main`; deploys are `./deploy.sh` on the instance, by hand.
  - The backend's domain is a free DuckDNS name (no bought domain).
- **Live since the evening of 2026-11-04:**

  | | |
  |---|---|
  | Web | <https://insights-hub-v2-one.vercel.app> (Vercel, root `web`, `NEXT_PUBLIC_MOCK=0`, both backend URLs set) |
  | Backend | <https://insighthubv2.duckdns.org> (Elastic IP, `/opt/insightshub`: compose with the backend + Caddy, Let's Encrypt certificate until 2027-02-02, renewed by Caddy) |
  | OAuth | A production GitHub OAuth app, separate from the dev one (callback `https://insighthubv2.duckdns.org/api/auth/callback`) |

  Checked from outside: health `UP` (200), `http://` → 308 to `https://`, `/actuator/env` 404, `/arena` 404, `/api/*` through Vercel returns the backend's problem+json 401, login redirects to GitHub with the production client id. The developer's first production login worked: Home with WIZARD LV 2, 2/3 hearts, streak 3.
- **Hero catch-up verified:** last settled 2026-10-18; the hourly job settled the missed days honestly to **LV 2** (from LV 5, as expected), and three active days since (Sun–Tue) won a heart back.
- **Measured:** the backend under the image's JVM flags peaked at 328 MB RSS through the full-stack e2e suite; on the instance the container sits at 387 MiB of its 700 MiB limit, idle CPU 0.4%. Startup takes ~30 s on the instance (4.9 s locally): Mumbai ↔ Neon in Ohio, ~200 ms per round trip, and a slower CPU.
- **Setup notes from the first deploy** (for `docs/deploy.md` next time): the instance runs Ubuntu (user `ubuntu`); a host-installed Caddy had to be stopped for the compose one; the first `.env` copied over held the laptop's dev values and two credentials the server doesn't need (`DOCKERHUB_TOKEN`, `GITHUB_TOKEN`): removed, renamed to `backend.env`, `JWT_SECRET` regenerated, `ENCRYPTION_KEY` kept (dev and prod share schema `v2`, so they must share the key). The deploy files can be fetched on the instance with `curl` from `raw.githubusercontent.com` (the repo is public) instead of `scp`.
- **Lessons:** a UI change that removes something the e2e specs assert must ship with its spec change in the same PR (#49/#50 were each red against the other half). A stale `web/.next` cache fails `next build`'s type check after a route is deleted. A value meant as a GitHub Actions *variable* added as a *secret* is invisible to `vars.*` (the image job now reads either).
- **Open for next session:**
  - 🤖 backend: the `prod` profile (`application-prod.yml`, HANDOFF REQUEST of 2026-11-04). Until it lands, the per-IP rate limits (`login`, `callback`, `unsubscribe`) see every visitor as Caddy and are shared.
  - 🤖 backend (HANDOFF REQUEST of 2026-11-05): **fewer LLM calls**: one combined call per user (5 → 1) at the first login; later logins, SAVE and redeploys make none; the daily job refreshes everyone's insights from GitHub and regenerates texts only when the data moved (class changed, a score ±5, commits/PRs/stars ±10%, top repos/languages changed, texts 7+ days old, or a fallback); B43 diagnostics.
  - Release checklist (`docs/deploy.md` §8): SAVE twice (cooldown), a Settings save; on the instance, the first 03:00 UTC refresh and the hourly hero runs in `docker compose --env-file deploy.env logs backend`; an AWS budget alert; Neon compute hours and Upstash commands after a few days; page latency (move v2 to a Neon project in Asia if it's slow).
  - Don't run the local backend against Neon while production runs (both would run the hourly jobs; digests could go out twice). Check whether `DIGEST_ENABLED` is on in production.
  - Docs: fold the setup notes above into `docs/deploy.md`.
  - 🤖 integration I6 (responsive/a11y on the live app); `docs/design/png/` re-render; SMTP for the digest; the older items (archetype calibration, v1 B26, GitGuardian).

### 2026-11-05: first live runs (🤖 integration)
- **Scheduled jobs live:** the hourly hero job at 01:05 UTC settled 1 day for 1 hero (`waitingForReauth=0`, nothing failed); the daily refresh started at 03:00:01 UTC as scheduled.
- **B43 (open):** the 03:00 refresh got a GitHub 401 for the developer and marked them `needs_reauth`, two hours after the hero job used the same token fine. Not a revocation (GitHub's security log), not a leak, not our code. The developer logs in again; diagnostics requested from backend.
- **Decided by the developer** (in three steps, the last one standing): every new user cost 5 LLM calls at each login past the cooldown. Now: **one combined call** per user for all five texts, made **inline at the first login** (real texts on the first Journal); afterwards **no LLM call on login, SAVE or redeploy**; the **daily job** fetches every user's insights from GitHub and regenerates the texts only when the data moved (thresholds: class changed, any score ±5, commits/PRs/stars ±10%, top repos/languages changed, texts 7+ days old, or a fallback), at most once a day. Not batching several users into one prompt (cross-user leakage, shared failure). Groq's Batch API noted for later. A redeploy already made no LLM calls. The digest intro already fits. REQUEST to backend.
