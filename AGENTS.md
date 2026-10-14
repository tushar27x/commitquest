# AGENTS.md

Insight Hub v2: a Spring Boot + Spring AI rewrite of the Insight Hub backend.
v1 reference (Python/FastAPI): `~/dev/insight_hub/server`. Roadmap and status: `PROGRESS.md`.

## Project index

_Last indexed: 2026-10-14, after Phase 6 (commit `a4922cb`)._

### What it is

Backend-only habit tracker for GitHub activity. `com.tushar27x:insightshub:0.0.1-SNAPSHOT`,
Java 21, Spring Boot 4.1.1, Spring AI 2.0.0 (BOM), Maven wrapper, virtual threads on.
Port of v1 (phases 0–9) plus new v2 features (2.6, 10–13). No frontend. HTTP surface:
`/api/auth/{login,callback,logout}` (public), `/api/user/insights` (JWT), `/actuator/health`.

### Status snapshot

- **Done:** Phases 0–6 (setup/config, health, JPA model steps 1–5, GitHub GraphQL client,
  insights engine + D&D archetype scoring, AI layer, GitHub OAuth + JWT). PRs #2–#6 merged to `main`.
- **Open v1 issue:** B26 — v1 production still calls Groq models that were retired.
- **Next up:** 🤖 Phase 7 (sync + Redis cache) · 🧑 Phase 2.6 (`V3__activity.sql`).
- Working agreement & phase details: `PROGRESS.md` (status table at the top, learning log at the bottom).

### Source layout (`src/main/java/com/tushar27x/insightshub/`)

| Package | Contents |
|---|---|
| `auth/` | `AuthController` (`/api/auth/*`, `oauth_state` cookie, constant-time state check), `AuthService` (authorize URL; code → user upsert with encrypted token → JWT), `GitHubOAuthClient` (token exchange, `GET /user`, `GET /user/emails`), `AuthProperties` (`insightshub.auth.*`: client id/secret, scopes `read:user user:email`, token TTL) |
| `ai/` | `AiReviewer` (5 generators — year in review, weekly, craft, repo summaries, character sheet — each with a static fallback), `AiInsightsService` (parallel execution, per-task timeout, crash isolation), `LlmChain`/`LlmProvider`/`ModelTier` (provider fallback chain), `AiConfig` (Groq primary + optional Gemini fallback), `AiProperties` (`insightshub.ai.*`), `CharacterSheet` |
| `config/` | `InsightsHubProperties` (`@ConfigurationProperties` record, `insightshub.*`), `ClockConfig` (injectable `Clock` bean) |
| `dev/` | `DevDataRunner` (dev-profile idempotent JPA round-trip check) |
| `entity/` | `Users`, `Insights` (`@MapsId`), enum `Archetype` (8 D&D classes) |
| `github/` | `GitHubGraphQlClient` (RestClient + `graphql/user-insights.graphql`, per-call token, rate-limit logging), `GitHubClientConfig`, `GitHubApiException`, `GitHubAuthException` (401 → Phase 10 `needs_reauth`) |
| `github/model/` | Typed records for the GraphQL response: `GitHubUser`, `UserInsightsData`, `GraphQlResponse` |
| `insights/` | `InsightsEngine` (pure logic: `GitHubUser` → `InsightsResult`), `UserStats` (snake_case via `@JsonNaming`, parity with v1), `HabitProfile` (15 habit signals, user's own commits only), `ScoringArchetypeClassifier` (0–100 per class, `ALGO_VERSION = 1`, ties → declaration order, all-zero → Rogue), `ArchetypeScores`, `ArchetypeClassifier` (interface) |
| `security/` | `SecurityConfig` (stateless, CSRF off, `/api/auth/**` + health public), `JwtConfig` (HS256 Nimbus encoder/decoder, issuer `insightshub`), `JwtService` (`sub`=GitHub id, `name`=login), `TokenCipher` (AES-256-GCM, GitHub id as AAD) |
| `user/` | `UserController` (`GET /api/user/insights`, user from JWT `sub` only; DTO in v1's snake_case shape) |
| `repository/` | `UsersRepository` (`findByAlertsEnabledTrue`, `findDueForSync`), `InsightsRepository` (`@EntityGraph`) |

### Resources

- `application.yml` — all config; secrets via `.env` (`spring.config.import`), git-ignored.
- `db/migration/` — Flyway owns the `v2` schema: `V1__users_and_insights.sql`, `V2__archetype_classes.sql`.
  Next migration is **`V3__activity.sql`** (Phase 2.6: `daily_contributions`, `commits`); `V2` is taken.
- `graphql/user-insights.graphql` — v1's query + `rateLimit`, commit `oid`/`committedDate`, `from`/`to` window.
- `prompts/*.st` — `persona.st` (supportive senior engineer, no roast) + one template per generator.

### Tests (`src/test/java/…`)

- Pure unit tests (always run): `InsightsEngineTest`, `HabitProfileTest`, `ScoringArchetypeClassifierTest`,
  `LlmChainTest`, `AiReviewerTest`, `AiInsightsServiceTest` (with `FakeChatModel`), `GitHubGraphQlClientTest` (`MockRestServiceServer`),
  `TokenCipherTest`, `JwtServiceTest`, `GitHubOAuthClientTest`, `AuthServiceTest`.
- `AuthApiTest` — `@WebMvcTest` slice with the real security chain (GitHub + repositories mocked, no DB).
  Config for slices goes on a nested `@TestConfiguration`, not the test class (B29).
- `InsightsEngineParityTest` — golden JSON against v1: `insights/v1-expected-insights.json` + `v1-parity-user.json`.
- Opt-in live tests (need env: `GITHUB_TOKEN`, `GROQ_API_KEY`): `GitHubGraphQlClientLiveTest`, `InsightsEngineLiveTest`, `AiLiveTest`.
- `InsightshubApplicationTests` boots the full context — needs DB/Redis/Groq; Testcontainers is Phase 14.
- ⚠️ B23: running the suite applies Flyway migrations to the **live Neon DB**.

### Infrastructure & config facts

- DB: production Neon Postgres, **schema `v2`** (v1 still runs from `public` on the same DB — never touch it).
  `ddl-auto: validate`; Flyway is the only schema writer.
- Redis: remote, SSL. Chat: Groq (OpenAI-compatible), `openai/gpt-oss-120b` primary / `gpt-oss-20b` fast,
  `reasoning_effort: low`; Gemini fallback dormant until `GEMINI_API_KEY` is set.
- Auth: GitHub OAuth App (callback `${BACKEND_URL}/api/auth/callback`), scopes `read:user user:email` (no `repo`).
  GitHub tokens are stored AES-GCM encrypted and never logged (B30); JWTs are HS256, 24 h.
- Embeddings/pgvector switched off (`spring.ai.model.embedding: none`, `vectorstore.type: none`) — stretch goal S1.

### Conventions

- One PR per phase: phases 3–9 implemented by the agent; Phase 2.6 and 10–13 by the developer (agent reviews).
- No AI-agent attribution in commits or PR descriptions (no co-author lines, no "generated with" footers).
- Bugs go to `BUGS.md` with the next `B` number (currently B1–B31); PR descriptions list the phase's bugs.
- No key- or token-shaped literals in code or tests, even fake ones: build test keys at runtime (B31).
- DTOs only from controllers (never entities); no secrets in logs; AI output always has a static fallback;
  the LLM never decides the archetype (code scores it, the LLM adds flavour).

### Commands

- `./mvnw spring-boot:run` — start (requires `.env`: Neon, Redis, `GROQ_API_KEY`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`).
- `./mvnw test` — unit tests (see B23 warning above).
- `./mvnw verify` — not green yet (integration tests arrive in Phase 14).

<!-- Remaining sections to be added as directed by the user. -->
