# Bug Log

Every bug hit while building Insight Hub v2: what we saw, why it happened, how it was fixed, and what to remember.
New bugs get the next `B` number. PR descriptions list the bugs found while building that phase.

| # | Phase | Bug | Kind |
|---|---|---|---|
| [B1](#b1) | 0 | Spring AI 1.0.0 doesn't support Spring Boot 4 | Dependency |
| [B2](#b2) | 0 | `insightshub:` indented under `spring:`, so properties bound to the wrong prefix | Config |
| [B3](#b3) | 0 | `@ConfigurationProperties` class with no setters/constructor bound nothing | Code |
| [B4](#b4) | 0 | `spring-dotenv` silently did nothing on Boot 4 | Dependency |
| [B5](#b5) | 0 | `.env` not git-ignored (caught before the first commit) | Security |
| [B6](#b6) | 0 | OpenAI starter failed on audio speech: "At least one credential source must be specified" | Config |
| [B7](#b7) | 0 | `vectorstore` nested under `spring.ai.model`, so it was ignored and pgvector demanded an `EmbeddingModel` | Config |
| [B8](#b8) | 0 | Groq `base-url` missing `/v1`, then with segments reversed | Config |
| [B9](#b9) | 1 | `REDIS_HOST` held a REST URL + stray quote: "Cannot parse port number" | Config |
| [B10](#b10) | 2 | `BigInteger` id failed schema validation against `BIGINT` | Code |
| [B11](#b11) | 2 | Entity defaults: missing no-arg constructor, null flags vs `NOT NULL` columns | Code |
| [B12](#b12) | 2 | `public void User(...)` was a method, not a constructor | Code |
| [B13](#b13) | 2 | Invalid derived query `existsUser` stopped startup | Code |
| [B14](#b14) | 2 | Sync query would skip never-synced users (`NULL < cutoff`) | Logic |
| [B15](#b15) | 2 | IntelliJ "Cannot resolve symbol 'log'" while Maven compiled fine | Tooling |
| [B16](#b16) | 2 | `createdAt` null right after `save()` | Code |
| [B17](#b17) | 2 | `LazyInitializationException ... no session` | Code |
| [B18](#b18) | 2 | `save(new Entity(existingId))`: silent overwrite for `Users`, duplicate key for `Insights` | Code |
| [B19](#b19) | 3 | `RestClient.Builder` auto-config only present via the Spring AI starter | Dependency |
| [B20](#b20) | 4 | **v1:** insights crash on any empty repository (`defaultBranchRef: null`) | Logic (v1) |
| [B21](#b21) | 4 | Phase 4 branch created from a stale local `main` | Process |
| [B22](#b22) | 4 | `reference to assertThat is ambiguous` with Jackson's `valueToTree` | Test code |
| [B23](#b23) | 4 | `./mvnw test` would apply unreviewed Flyway migrations to the live Neon DB | Process (latent) |
| [B24](#b24) | 4 | Renaming enum constants broke `DevDataRunner` (`cannot find symbol CODE_CRUSADER`) | Code |
| [B25](#b25) | 5 | `NoClassDefFoundError` for a class that had just compiled (IntelliJ build racing Maven) | Tooling |
| [B26](#b26) | 5 | **v1 + v2:** Groq retired `llama-3.3-70b-versatile` / `llama-3.1-8b-instant` (404) | Dependency (external) |
| [B27](#b27) | 5 | Reasoning model spent all of `max_tokens` thinking and returned empty content | Config |
| [B28](#b28) | 5 | The LLM assumed a user's gender ("He") in generated text | Prompt |
| [B29](#b29) | 6 | `@EnableConfigurationProperties` on a `@WebMvcTest` class was ignored: "No qualifying bean of type InsightsHubProperties" | Test code |
| [B30](#b30) | 6 | **v1:** the OAuth callback prints every user's GitHub access token to the logs | Security (v1) |
| [B31](#b31) | 6 | GitGuardian flagged two fake test AES keys as leaked secrets | Process |
| [B32](#b32) | 7 | `RedisCacheManagerBuilderCustomizer` not on the classpath: Boot 4 moved caching into its own starter | Dependency |
| [B33](#b33) | 7 | Removing the "unused" `spring-boot-starter-security-test` broke all `@WebMvcTest` slices | Dependency |
| [B34](#b34) | 9 | Spring 7 `ProblemDetail` has no `type` by default, so error bodies lacked a key the contract lists | Dependency |
| [B35](#b35) | 9 | Validation errors named Java fields (`restDays`), not the API's snake_case keys | Code |
| [B36](#b36) | 2.6 | `daily_contributions` SQL is unqualified and resolves outside `v2`, so every sync fails at the activity write | Code |
| [B40](#b40) | 14 (B9) | springdoc failed with `NoSuchMethodError: Schema.$dynamicRef()`: Spring AI pulls older swagger annotations | Dependency |
| [B41](#b41) | 14 (B9) | The OpenAPI schemas showed camelCase keys: swagger-core can't see Jackson 3's `@JsonNaming` | Dependency |
| [B42](#b42) | U10 | The top row of CLASS SCORES drew a shorter bar than the others | UI |
| [B43](#b43) | 14 (live) | The live daily refresh marked the developer `needs_reauth`: GitHub 401 on a token that worked two hours earlier | Open |
| [B44](#b44) | 14 (B11) | Every context failed to start: a second constructor on the `AiProperties` record hid its binding constructor | Config |
| [B45](#b45) | 14 (B12) | A short `users` write could put back the GitHub token a login had just replaced, and a job's 401 could flag a user who had just logged in | Concurrency |

---

### B1
**Spring AI 1.0.0 doesn't support Spring Boot 4** · Phase 0 · Dependency
- **Symptom:** caught during planning, before it could break anything. Spring AI 1.0.x targets Boot 3 (Spring Framework 6, Jackson 2); the project uses Boot 4 (Framework 7, Jackson 3).
- **Fix:** `spring-ai.version` → `2.0.0`.
- **Lesson:** after a major Boot upgrade, check every Spring-ecosystem library's supported Boot line first.

### B2
**Properties bound to `spring.insightshub` instead of `insightshub`** · Phase 0 · Config
- **Symptom:** `@ConfigurationProperties(prefix = "insightshub")` received nothing.
- **Root cause:** in `application.yml`, the `insightshub:` block was indented under `spring:`.
- **Fix:** made `insightshub:` a top-level key.
- **Lesson:** YAML nesting *is* the property name. Spring ignores keys it doesn't recognize, so check indentation first.

### B3
**`InsightsHubProperties` couldn't bind** · Phase 0 · Code
- **Root cause:** a plain class with package-private fields and no setters or constructor, so Spring had no way to set the values.
- **Fix:** turned it into a `record` (constructor binding).
- **Lesson:** records are the simplest immutable way to bind configuration.

### B4
**`.env` not loaded: DB login as the literal user `${DB_USER}`** · Phase 0 · Dependency
- **Symptom:** `password authentication failed for user '${DB_USER}'`, shown at the bottom as `Unable to determine Dialect without JDBC metadata`.
- **Root cause:** `spring-dotenv` 4.0.0 (2023) was built against Boot 3. Boot 4 moved `ConfigurableBootstrapContext` to `org.springframework.boot.bootstrap`, so the library's listener method no longer overrode anything and never ran. No error was raised.
- **Fix:** removed the library; `spring.config.import: "optional:file:.env[.properties]"`.
- **Lesson:** the real cause is at the first `WARN`/`Caused by`, not the last stack trace. Prefer built-in features over small libraries across major upgrades.

### B5
**`.env` not git-ignored** · Phase 0 · Security
- **Symptom:** found in review before `git init`.
- **Fix:** rewrote `.gitignore` with secrets first (`.env`, `.env.*`, keys, local profiles); verified `.env` was never committed.
- **Lesson:** set up `.gitignore` before the first `git add .`, especially for a public repo.

### B6
**`OpenAiAudioSpeechAutoConfiguration`: "At least one credential source must be specified"** · Phase 0 · Config
- **Root cause:** the OpenAI starter auto-configures chat, embedding, image, audio speech, transcription and moderation models, each needing a key. None was configured.
- **Fix:** Groq API key + base URL for chat; `spring.ai.model.{audio.speech,audio.transcription,image,moderation,embedding}: none`. (First fixed with `spring.autoconfigure.exclude`, then switched to these properties, which survive class renames.)
- **Lesson:** a starter switches features on; properties or excludes switch them off.

### B7
**pgvector: "required a bean of type `EmbeddingModel` that could not be found"** · Phase 0 · Config
- **Root cause:** `vectorstore: type: none` was nested under `spring.ai.model`, so the real key `spring.ai.vectorstore.type` was never set. With embeddings disabled, pgvector still loaded and had no embedding model.
- **Fix:** moved it to `spring.ai.vectorstore.type: none`.
- **Lesson:** same as B2: an unknown key is ignored silently, and the symptom shows up somewhere else.

### B8
**Groq base URL wrong** · Phase 0 · Config
- **Symptom:** no error at startup (no API call is made); found by probing the endpoints.
- **Root cause:** Spring AI 2.0's default base URL includes `/v1` (`https://api.openai.com/v1`). `https://api.groq.com/openai` and `https://api.groq.com/v1/openai` both return 404.
- **Fix:** `https://api.groq.com/openai/v1` (returns 401 without a key, i.e. the endpoint exists).
- **Lesson:** "the app starts" doesn't mean an external integration works.

### B9
**`Cannot parse port number: https://<host>.upstash.io"`** · Phase 1 · Config
- **Symptom:** the first request using Redis returned 500.
- **Root cause:** `REDIS_HOST` held Upstash's REST URL (`https://...`) plus a stray `"`. Lettuce speaks the Redis protocol and needs a bare hostname.
- **Fix:** bare hostname in `.env`; port 6379 and TLS stay in `application.yml`.
- **Lesson:** Redis connects **lazily** (first command); Postgres connects **eagerly** at startup. The bad value had been there since Phase 0.

### B10
**`wrong column type ... [github_id]; found [int8], but expecting [numeric(38,0)]`** · Phase 2 · Code
- **Root cause:** `BigInteger` maps to SQL `NUMERIC`.
- **Fix:** `Long` for `BIGINT`.
- **Lesson:** `ddl-auto: validate` catches type mismatches at startup, so keep it on.

### B11
**Entity construction and defaults** · Phase 2 · Code (found in review)
- **Problems:** `@AllArgsConstructor` removed the no-arg constructor JPA needs; `Boolean` fields defaulted to `null` against `NOT NULL` columns; same for `Insights.stats` and `updatedAt`.
- **Root cause:** a DB `DEFAULT` only applies when the INSERT *leaves the column out*. Hibernate sends every column, so `null` is sent.
- **Fix:** protected no-arg constructor + public constructor for the required fields; primitive flags with Java defaults; `stats = new HashMap<>()`; `@UpdateTimestamp`.

### B12
**`public void User(Long, String)` isn't a constructor** · Phase 2 · Code
- **Root cause:** it has a return type, and the class is named `Users`. Java allows methods named like a class, so it compiled silently.
- **Fix:** `public Users(Long githubId, String login)`.

### B13
**`No property 'existsUser' found for type 'Users'`** · Phase 2 · Code
- **Root cause:** Spring Data derived queries must follow `existsBy<Property>`; anything else is parsed as a property name.
- **Fix:** removed it; `existsById` and `findById` are inherited from `JpaRepository`.
- **Lesson:** repository methods are validated at startup, after the entity schema check.

### B14
**Never-synced users would be skipped by the sync query** · Phase 2 · Logic (found in review)
- **Root cause:** `findByNeedsReauthFalseAndLastSyncedAtBefore`: `NULL < cutoff` isn't true in SQL. Adding `Or...IsNull` binds wrongly, because `OR` has lower precedence than `AND`.
- **Fix:** `@Query`: `needsReauth = false and (lastSyncedAt is null or lastSyncedAt < :cutoff)`.
- **Lesson:** derived method names can't express brackets.

### B15
**IntelliJ: `Cannot resolve symbol 'log'` / `getGithubId`, while `./mvnw compile` passed** · Phase 2 · Tooling
- **Root cause:** Arch's `intellij-idea-community-edition` (IC-262.10315.125) doesn't bundle the Lombok plugin. Annotation processing settings were already correct.
- **Fix:** installed JetBrains' Lombok plugin 262.8665.176 (compatible with `262.*`) into the IDE's user plugin folder.
- **Lesson:** IDE errors + green Maven build = IDE setup problem. An IntelliJ update may require reinstalling the plugin from the Marketplace.

### B16
**`createdAt=null` right after `save()`** · Phase 2 · Code
- **Root cause:** the DB sets it (`DEFAULT now()`), but Hibernate doesn't read back columns it didn't write (`insertable = false`).
- **Fix:** `@Generated(event = EventType.INSERT)`.

### B17
**`LazyInitializationException: Could not initialize proxy [Users#...] - no session`** · Phase 2 · Code
- **Root cause:** `findById` runs in its own transaction; `Insights.user` is a LAZY proxy, and the session was closed when `getLogin()` tried to load it.
- **Fix:** `@EntityGraph(attributePaths = "user")` on `InsightsRepository.findById`. Also set `spring.jpa.open-in-view: false`, so web requests behave the same instead of hiding this.
- **Lesson:** load what you need inside the transaction (fetch graph / fetch join / service `@Transactional`).

### B18
**Re-running `save(new ...)` on existing rows** · Phase 2 · Code
- **Symptoms:** `Users`: no error, but `merge` copied the new object's default preferences over the stored row (a silent overwrite). `Insights`: `duplicate key value violates unique constraint "insights_pkey"`.
- **Root cause:** Spring Data's `save()` decides "new" by the id. `Users` had its id set → `merge`; `Insights` had a null id until `@MapsId` filled it → `persist` → INSERT.
- **Fix:** find-or-create (`findById(id).orElseGet(...)`), then modify and save.
- **Lesson:** for existing rows, always load and then update. Never `save(new Entity(existingId))`.

### B19
**`RestClient.Builder` auto-config only present through the Spring AI starter** · Phase 3 · Dependency (latent, no failure yet)
- **Found by:** `./mvnw dependency:tree` while building the GitHub client. `spring-boot-restclient` (which auto-configures `RestClient.Builder` in Boot 4) came in only via `spring-ai-starter-model-openai → spring-boot-starter-restclient`.
- **Risk:** removing or changing the AI starter would make startup fail with "no bean of type `RestClient.Builder`" in code unrelated to AI.
- **Fix:** declared `spring-boot-starter-restclient` directly in `pom.xml`.
- **Lesson:** Boot 4 splits auto-config into modules. Declare a starter for every feature you use directly; don't rely on transitive ones.

### B20
**v1: `calculate_user_insights` crashes on any empty repository** · Phase 4 · Logic (bug in v1, found while porting)
- **Symptom:** running v1's real Python code on the Phase 3 fixture: `AttributeError: 'NoneType' object has no attribute 'get'` at `repo.get("defaultBranchRef", {}).get("target", {})`.
- **Root cause:** GitHub returns `"defaultBranchRef": null` for a repo with no commits. Python's `dict.get(key, default)` only uses the default when the key is *missing*, not when its value is `null`.
- **Impact in v1 production:** `routes.py` swallows the exception in its broad `try/except`, so any user with an empty repo among their top 5 by stars logs in fine but **never gets insights saved**, silently.
- **Fix (v2):** `InsightsEngine` treats every nested GitHub object as nullable; covered by `InsightsEngineTest.emptyRepositoryDoesNotCrash_B20`. v1 itself is unchanged.
- **Lesson:** `x.get(k, {})` is not null-safe in Python, and a blanket `except` turns crashes into silent data loss.

### B21
**Phase 4 branch created from a stale `main`** · Phase 4 · Process
- **Symptom:** the Phase 3 test fixture was missing on the new branch.
- **Root cause:** local `main` was 4 commits behind `origin/main` (the PR #2 merge wasn't pulled), and the branch was created from the Phase 3 branch tip instead of the merge. The checkout had also changed between commands (likely IDE git activity during the merge).
- **Fix:** fast-forwarded `main` (`git merge --ff-only origin/main`) and recreated the branch from `origin/main` before any commits.
- **Lesson:** before branching, verify `git rev-parse --abbrev-ref HEAD` and `git status -sb` show `main` level with `origin/main`.

### B22
**`reference to assertThat is ambiguous`** · Phase 4 · Test code
- **Root cause:** `JsonMapper.valueToTree(...)` returns a generic `<T extends JsonNode> T`. Passed straight to AssertJ's overloaded `assertThat`, the compiler can't infer `T` (both `Predicate` and `IntPredicate` overloads match).
- **Fix:** assign the result to a `JsonNode` variable first.

### B23
**`./mvnw test` would apply unreviewed Flyway migrations to the live Neon DB** · Phase 4 · Process (latent, caught before it happened)
- **Found by:** about to run the full suite after adding `V2__archetype_classes.sql`. `InsightshubApplicationTests.contextLoads()` is a `@SpringBootTest` that starts the whole app with the real datasource, so Flyway would migrate Neon as a side effect of running the tests.
- **Risk:** a migration applied before review can't be changed afterwards. Flyway checksums applied migrations and refuses to start when a file differs, which forces a `flyway repair` or a fix-up migration.
- **Workaround (this PR):** ran the unit tests with `-Dtest='!InsightshubApplicationTests'`, and dry-ran V2 on Neon inside `BEGIN … ROLLBACK` (Postgres DDL is transactional), so nothing was committed. A local Docker Postgres wasn't usable (no permission on `/var/run/docker.sock`).
- **Proper fix (Phase 14):** Testcontainers + `@ServiceConnection`, so tests never touch Neon. Until then, exclude `contextLoads` when a PR adds a migration.
- **Fixed in I2:** `contextLoads` became `it/ApplicationContextIT` on Testcontainers Postgres + Redis (`./mvnw verify`, Docker needed). Surefire and Failsafe also point `spring.datasource.url` and the Redis host at unresolvable `*.invalid` hosts, so a test that boots the app without the containers fails to connect instead of reaching Neon. `./mvnw test` is unit-only and safe to run in full.
- **Lesson:** a "does the context load?" test against a shared database isn't harmless: it runs every startup side effect, migrations included.

### B24
**Renaming enum constants broke `DevDataRunner`** · Phase 4 · Code
- **Symptom:** `cannot find symbol: variable CODE_CRUSADER` in `dev/DevDataRunner.java` after replacing the archetypes.
- **Root cause:** the dev runner hard-codes an archetype, and only the `insights` package had been searched for usages.
- **Fix:** use `Archetype.PALADIN`, the same mapping as the V2 migration (`CODE_CRUSADER → PALADIN`).
- **Lesson:** after renaming or removing an enum constant, grep the whole source tree, including `dev`/profile-only code that normal tests don't exercise.

### B25
**`NoClassDefFoundError: com/tushar27x/insightshub/ai/AiConfig`** · Phase 5 · Tooling
- **Symptom:** live tests failed before any HTTP call, for a class that compiled fine. `target/classes/.../AiConfig.class` existed right afterwards.
- **Root cause:** IntelliJ was open and rebuilt the just-edited `AiConfig.java` into the same `target/classes` while Maven's test JVM was running. Timestamps: test report `13:22:10.706`, class file rewritten `13:22:10.989`. The file was briefly missing.
- **Fix:** rerun. Nothing in the code.
- **Happened again** with resources during the persona change: `FileNotFoundException: prompts/weekly-review.st` for a file that was in `src/` and showed up in `target/classes` right after the run (IntelliJ copied it at `14:26:11`). Rerunning fixed it.
- **Lesson:** an IDE and Maven sharing `target/` can race. If a "class not found" makes no sense, compare timestamps before debugging code. (Optional: in IntelliJ, delegate build/run to Maven, or turn off "Build project automatically".)

### B26
**Groq retired v1's models: `404: The model 'llama-3.3-70b-versatile' does not exist or you do not have access to it`** · Phase 5 · Dependency (external)
- **Found by:** the first live run of `AiLiveTest`. The key was valid (a fake key gets `401`, the real one got `404` for the model).
- **Root cause:** `GET /openai/v1/models` for the key lists no Llama 3.x chat models any more: only `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b` and a few non-chat models.
- **Impact on v1 production:** v1 hard-codes both model names, and its `except` blocks fall back to Gemini, then to static text. Unless its Gemini fallback works, **v1 users currently get "Your code is so average it didn't even trigger my fallback circuits"** instead of real reviews, with no error visible.
- **Fix (v2):** `spring.ai.openai.chat.options.model: openai/gpt-oss-120b`, `insightshub.ai.fast-model: openai/gpt-oss-20b`. Model names live in config, not code.
- **Lesson:** hosted model names are not stable. Keep them in config, and have a live smoke test (and, later, an alert) for "every answer is a fallback".

### B27
**Reasoning model returned empty content (`finish_reason: length`)** · Phase 5 · Config
- **Symptom:** a direct call to `openai/gpt-oss-20b` with v1's 200-token cap used 198 tokens on hidden reasoning and returned `""`. The chain treats blank as a failure, so every repo summary would have silently become the default text.
- **Root cause:** reasoning tokens count against `max_tokens`. v1's caps (100–300) were sized for non-reasoning Llama.
- **Fix:** per-provider `reasoning_effort: low` (`insightshub.ai.reasoning-effort`), and `LlmChain.REASONING_HEADROOM` (+300 tokens) when an effort is set. It's per provider because non-reasoning fallback models may reject the parameter.
- **Lesson:** when switching to a reasoning model, retune token limits; "answer length" and "token budget" are no longer the same thing.

### B28
**The LLM assumed a user's gender** · Phase 5 · Prompt
- **Symptom:** live character-sheet lore for `octocat`: "He shows up every workday...".
- **Root cause:** v1's persona said nothing about how to refer to the developer, and these texts describe real users.
- **Fix:** persona line: address the developer as "you" or by login; use "they" if a pronoun is needed. The rerun said "They've been glued to the terminal...".
- **Lesson:** review generated text about real people for assumptions, not only for format.

### B29
**`No qualifying bean of type 'InsightsHubProperties'` in `AuthApiTest`** · Phase 6 · Test code
- **Symptom:** all 12 `@WebMvcTest` tests failed to load the context. Adding `@EnableConfigurationProperties` to the test class changed nothing.
- **Root cause:** a slice test only registers the slice's beans; the main class's `@ConfigurationPropertiesScan` doesn't apply. And annotations on the test class itself are not read as configuration, so `@EnableConfigurationProperties` there is ignored.
- **Fix:** a nested `@TestConfiguration` carrying `@EnableConfigurationProperties({InsightsHubProperties.class, AuthProperties.class})`, added to `@Import`.
- **Lesson:** in slice tests, put configuration on a `@TestConfiguration` class, never on the test class.

### B30
**v1 logs every user's GitHub access token** · Phase 6 · Security (v1)
- **Found by:** porting v1's callback (`app/api/routes.py`, line 102): `print("GitHub token response:", token_data)`. `token_data` is GitHub's token response, including `access_token` (scope `repo`: read/write to private repos).
- **Impact on v1 production:** every login writes a live token to the host's logs, readable by anyone with log access.
- **Fix (v2):** tokens never appear in logs or exception messages. The token exchange error carries only GitHub's `error` code (e.g. `bad_verification_code`). Tests assert the token isn't in error messages or the JWT.
- **Fix (v1, suggested):** delete the `print` line, and treat the logged tokens as leaked: users can revoke at github.com/settings/applications, or the OAuth App's tokens can be reset in its settings.
- **Lesson:** never log whole responses from auth endpoints; log the fields you need.

### B31
**GitGuardian: "Generic Encryption Key" in `SecurityTestData.java`** · Phase 6 · Process
- **Symptom:** the GitGuardian check failed on PR #6 with 2 incidents.
- **Root cause:** the test keys were 44-character Base64 literals in fields named `*_ENCRYPTION_KEY`. They were made up (one decodes to the bytes 0–31), and no `.env` value appears anywhere in the PR (checked value by value), but a scanner can't tell a fake key from a real one.
- **Fix:** test keys are built at runtime from byte sequences; `AuthApiTest` gets its key through `@DynamicPropertySource`. Folded into the feat commit and force-pushed, so the literals never appear in the branch history. The two existing incidents still need resolving as test credentials in the GitGuardian dashboard.
- **Lesson:** never write key-shaped literals, even fake ones; generate test keys in code. When a scanner fires, first check whether any real value leaked before rewriting anything.

### B32
**`RedisCacheManagerBuilderCustomizer` not found** · Phase 7 · Dependency
- **Symptom:** the class Boot 3 docs point to (`org.springframework.boot.autoconfigure.cache`) wasn't in any jar on the classpath, although Redis and Spring's `@Cacheable` were.
- **Root cause:** Boot 4 split auto-configuration into modules. Cache auto-configuration (including the Redis cache manager) now lives in `spring-boot-cache`, brought in by `spring-boot-starter-cache`, under `org.springframework.boot.cache.autoconfigure`. `spring-boot-starter-data-redis` alone no longer gives you a `CacheManager`.
- **Fix:** add `spring-boot-starter-cache`, plus `spring.cache.type: redis` so a missing Redis cache fails at startup instead of silently falling back to an in-memory cache.
- **Lesson:** on Boot 4, when a Boot 3 class is missing, look for a `spring-boot-<feature>` module and its starter (same story as B19).

### B33
**Removing an "unused" test dependency broke every `@WebMvcTest`** · Phase 7 · Dependency
- **Symptom:** after removing `spring-boot-starter-security-test` (no class in `src/` imported it), all 17 `AuthApiTest` tests failed: `No qualifying bean of type 'HttpSecurity'` while creating `SecurityConfig.apiSecurity`.
- **Root cause:** the module is used without being imported. Its `META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest.includes` and `...AutoConfigureMockMvc.imports` files add Spring Security's auto-configuration (`SecurityAutoConfiguration`, `ServletWebSecurityAutoConfiguration`, ...) to `@WebMvcTest` slices. Without them, the slice has no Spring Security, so no `HttpSecurity` builder.
- **Fix:** kept the dependency, with a pom comment saying why.
- **Lesson:** "no imports" doesn't mean "unused" for Spring Boot starters; they often contribute auto-configuration through `META-INF/spring/*.imports` files. Run the tests before removing a dependency. Same Boot 4 module split as B19 and B32.

### B34
**Spring 7 `ProblemDetail` has no `type` by default** · Phase 9 · Dependency
- **Symptom:** the first `ApiExceptionHandlerTest` run failed 8 of 8 on `No value at JSON path "$.type"`, and `ProblemDetails.write` threw a `NullPointerException` on `getType().toString()`.
- **Root cause:** in Spring Framework 7, `ProblemDetail.forStatus(...)` leaves `type` null, and the JSON leaves it out. RFC 9457 reads a missing `type` as `about:blank`, so this is valid, but `docs/api/contract.md` lists `type` in every error body. (Spring 6 filled in `about:blank`.)
- **Fix:** `ProblemDetails.complete` sets `type: about:blank` and `instance` whenever they're missing. Every error path goes through it: the controller advice, the `/error` controller, the 401/403 writers and the rate-limit interceptor.
- **Lesson:** test the exact JSON shape of an error, not just the status code. Defaults like this changed between Spring 6 and 7 without any compile error.

### B35
**Validation errors named Java fields, not the API's keys** · Phase 9 · Code
- **Symptom:** a test body with an invalid `restDays` came back as `errors[].field = "restDays"`, while every key in the API is snake_case (`rest_days`).
- **Root cause:** Bean Validation reports the Java property path. The DTOs map to snake_case through `@JsonNaming`, which validation doesn't know about.
- **Fix:** `ApiExceptionHandler` converts each field path to snake_case (`week[0].restDays` → `week[0].rest_days`). Found before any endpoint had a request body; B5's `PUT /preferences` is the first one.
- **Lesson:** error payloads have to use the same names as the request the client sent.

### B36
**`daily_contributions` SQL is unqualified and resolves outside `v2`, so every sync fails at the activity write** · Phase 2.6 (B1) · Code · found by integration in I2, fixed in #16
- **Symptom:** `DailyContributionsRepositoryIT` against a real Postgres 17: `ERROR: relation "daily_contributions" does not exist` on both the upsert and `latestDate`. `SyncService.sync` calls `ActivityWriter.writeCalendar` without a catch, so a sync throws there: `POST /api/user/sync` → 500, and the login sync fails (logged, login still succeeds).
- **Root cause:** `DailyContributionsRepository` uses plain JDBC with unqualified table names. Only JPA has `hibernate.default_schema: v2`, Flyway sets the schema only while migrating, and neither the JDBC URL nor the pool sets `search_path`. A connection's default `search_path` is `"$user", public`, and the table lives in `v2`. Not checked against Neon (the integration lane doesn't connect to it), but nothing in the config would make it different there.
- **Why the unit tests missed it:** `DailyContributionsRepositoryTest` and `ActivityWriterTest` mock `JdbcTemplate` and the repository, so no SQL ever ran.
- **Fix (backend):** qualify the SQL (`v2.daily_contributions`), or set the schema for every pooled connection (`spring.datasource.hikari.connection-init-sql` or the `schema` property). A `currentSchema` parameter in the JDBC URL would fix Neon but not the tests, because `@ServiceConnection` supplies its own URL. Then remove `@Disabled("B36 …")` from `it/DailyContributionsRepositoryIT`.
- **Fixed in #16:** the repository qualifies the table with the Flyway schema (`spring.flyway.default-schema`, checked to be a plain identifier), so plain JDBC finds `v2.daily_contributions`. Chosen over `search_path`: Neon's pooler (PgBouncer, transaction mode) doesn't reliably keep session settings. `@Disabled` removed from `it/DailyContributionsRepositoryIT` in the follow-up.
- **Lesson:** SQL that bypasses JPA needs a test against a real database. Mocked `JdbcTemplate` tests check what you send, not whether Postgres accepts it.

### B37
**The hero job would have settled most users' days without their last hours of activity** · Phase 11 (B5) · Design · found while building B5, before it shipped
- **Symptom (by reasoning, not seen live):** the hourly job settles day D at 06:00 local on D+1 from `daily_contributions`. Those rows were only refreshed by the user's own sync (login, SAVE) and by the daily sync at 03:00 UTC. In Kolkata, D is settled at 00:30 UTC, before that day's 03:00 sync, so D's last sync was the previous 03:00 UTC (08:30 local on D). In Los Angeles the 03:00 UTC sync is 20:00 local on D. Anyone who commits in the evening and doesn't open the app would lose a heart for a day they were active.
- **Root cause:** the plan runs the sync on a UTC clock and the evaluation on each user's local clock; nothing ties the freshness of the data to the day being settled.
- **Fix:** `HeroJob` first refreshes each due user's activity (`SyncService.refreshActivity`: the calendar from the day before the first due day, plus the branch scan in the user's zone; no AI and no cooldown), and settles only if that refresh succeeded. GitHub down: the days wait for the next hourly run. Token revoked: the hero waits until the user logs in again.
- **Lesson:** when data is fetched on one clock and consumed on another, check its freshness at the moment of use, per time zone.

### B38
**`?mock_error` does nothing under `next dev`** · U1/U2 (mock mode) · Code · found by integration in I5, fixed in `ui/b38-mock-error`
- **Symptom:** `npm run dev` with `NEXT_PUBLIC_MOCK=1`, then `/home?mock_error=401` (or 404, 429): Home renders normally, with no "Session ended", NEW GAME or "road blocked" state. The same URLs work on a production build (`next build && next start`), which is what the I5 mock suite now runs on.
- **Root cause:** `takeForcedError` (`web/lib/mock.ts`) fails only the first API call of the page load (`forcedErrorUsed`). In development, React StrictMode mounts every component twice, so the first fetch belongs to a query that is cancelled and fetched again. The forced error goes to the cancelled fetch, and the second one succeeds.
- **Fix (ui):** count the forced error as used only once its response is delivered (skip it when `init.signal` is aborted), or fail the first call per endpoint. `docs/run-locally.md` advertises `?mock_error` for `npm run dev`, so that's where it has to work.
- **Fixed in `ui/b38-mock-error`:** a call only *claims* the forced error, then waits one tick (`delay(0, signal)`). If its `signal` aborts first, an abort listener releases the claim and the call rejects like a cancelled `fetch`, so the refetch gets the error; otherwise it's marked used and the problem is returned. Still one error per page load, also with concurrent calls (the claim is taken synchronously). Unit test: an aborted first fetch leaves the error for the next one (`lib/mock.test.ts`). Checked under `next dev` (401, 404, 429 states show) and by the I5 mock suite on the production build.
- **Lesson:** dev-only tooling needs a test in the mode developers actually use; StrictMode's double mount defeats any "first call only" state kept at module level.

### B39
**Files under `prompts/` and every `README.md` are silently left out of commits** · Repo setup · Process · found by integration while reviewing #35
- **Symptom:** #35's CI failed on `FileNotFoundException: prompts/digest-intro.st` although `./mvnw verify` passed locally and `git status` was clean. `docs/design/README.md` (the design index) and `web/README.md` were never committed either; #34 says "Web README updated", but the file isn't in its diff.
- **Root cause:** the shared repository's `.git/info/exclude` (all worktrees use it) has unanchored `README.md` and `prompts/` lines under "Claude kit (local only)". They match `docs/design/README.md`, `web/README.md` and `src/main/resources/prompts/`. Files that were already tracked stay tracked; new ones never show up.
- **Fix:** `git add -f` for the missing files (#35: `b418b0e`; I5 adds `docs/design/README.md`; `web/README.md` waits on the ui). The developer's call: anchor the two lines (`/README.md`, `/prompts/`) or remove them.
- **Lesson:** a green local build doesn't prove that the commit is complete; CI on a clean checkout does. When a file "can't be found" only in CI, run `git ls-files --others --ignored --exclude-standard`.

### B40
**springdoc failed with `NoSuchMethodError: io.swagger.v3.oas.annotations.media.Schema.$dynamicRef()`** · Phase 14 (B9) · Dependency · found while building B9
- **Symptom:** with springdoc-openapi 3.2.0 added, building the OpenAPI document failed as soon as a class carried a swagger `@Schema` annotation.
- **Root cause:** two clashes on the swagger annotations. Spring AI 2.0.0 (`spring-ai-model`) depends on `swagger-annotations-jakarta` 2.2.38, nearer in the tree than springdoc's 2.2.55, so Maven picked 2.2.38, and swagger-core 2.2.55 calls annotation methods that 2.2.38 lacks. And `openai-java-core` (Spring AI's OpenAI client) adds `swagger-annotations` 2.2.31, the javax flavour with the same package and class names, so the classpath held two copies of each annotation.
- **Fix:** `pom.xml` pins `swagger-annotations-jakarta` to `${swagger-core.version}` (2.2.55, the one springdoc 3.2.0 is built on) in `dependencyManagement`, and excludes `swagger-annotations` from `spring-ai-starter-model-openai`. `./mvnw dependency:tree | grep swagger-annotations` now shows one jar, 2.2.55.
- **Lesson:** Maven resolves by nearest wins, not newest, and a jar of "only annotations" can still break at runtime. After adding a starter, grep the tree for duplicate packages under different artifact ids. Bump `swagger-core.version` together with `springdoc.version`.

### B41
**The OpenAPI schemas showed camelCase keys (`xpForNext`), not the API's snake_case** · Phase 14 (B9) · Dependency · found while building B9
- **Symptom:** the first `/v3/api-docs` listed `xpForNext`, `restDays`, `lastEvaluatedOn`, ... although the API sends `xp_for_next`, `rest_days`, `last_evaluated_on`. A client generated from the docs would have been wrong on every multi-word field.
- **Root cause:** the DTOs use Jackson 3's `tools.jackson.databind.annotation.JsonNaming` (Boot 4). swagger-core, under springdoc 3.2.0, still introspects models with Jackson 2, which only knows `com.fasterxml.jackson.databind.annotation.JsonNaming`. The annotations both versions share (`@JsonProperty`, `@JsonValue`, in `com.fasterxml.jackson.annotation`) were honoured, so `class` and the lower-case week statuses came out right, which hid the problem at a glance.
- **Fix:** `OpenApiConfig.modelResolver` gives swagger-core a Jackson 2 mapper whose introspector also reads the Jackson 3 `@JsonNaming` and maps it to the Jackson 2 strategy (an unknown strategy fails loudly). `OpenApiDocsTest` compares every response schema's keys with the contract fixtures, so a camelCase key fails the build.
- **Lesson:** during the Jackson 2 to 3 transition, a tool that reads your annotations may be on the other major version. Check generated docs against real payloads, not by eye.

### B42
**The top row of CLASS SCORES drew a shorter bar than the others** · U10 · UI · found in the developer's Hero screenshot
- **Symptom:** on `/hero` the highlighted (top score) row's segment bar looked shorter and didn't line up with the bars under it, so the gold box looked misaligned.
- **Root cause:** the highlighted row sits on `bg-stone-lit`, which is also the colour of the bar's empty cells, so they vanished into the row and only the filled cells showed.
- **Fix:** the top row's empty cells use `bg-stone`, a shade darker than the row, so its bar keeps the full 10 (phone) or 20 (desktop) cells.
- **Lesson:** a highlight background must differ from every fill drawn on it; check the "empty" states of meters on highlighted rows.

### B43
**The live daily refresh marked the developer `needs_reauth`: GitHub answered 401 on a token that worked two hours earlier** · Phase 14 (first live run, 2026-11-05) · **Open**
- **Symptom:** in production, the hourly hero job at 01:05 UTC refreshed the developer's activity with the stored token and settled a day (`waitingForReauth=0`). The daily refresh at 03:00:02 UTC got HTTP 401 from GitHub for the same user (`Activity refresh for 'tushar27x': GitHub rejected the stored token; marked needs_reauth`, summary `needsReauth=1`). The hero job skips the user until they log in again.
- **Ruled out:** only a real GitHub 401 sets `needs_reauth` (`GitHubGraphQlClient`, `GitHubOAuthClient`); a decryption failure or a network error doesn't. Our app never revokes tokens (LOG OUT only drops the JWT). GitHub's security log shows no `oauth_authorization.destroy` for the production app (`insighthubv2`) after its creation at the production login, and none by "GitHub System" (secret scanning), so the authorization wasn't revoked and the token didn't leak.
- **Still open:** whether anything logged in between 01:05 and 03:00 UTC (each login stores a new token in the user's row), and what the Authorized OAuth Apps page says about the app. Next: the developer logs in again; watch the next 01:05 and 03:00 runs. If it recurs with no login, it's ours.
- **Asked of backend:** on a GitHub 401, log GitHub's `X-GitHub-Request-Id` and error message (traceable by GitHub support), and keep when the stored token was obtained (a timestamp, never the token), so the next occurrence can be pinned to a token.
- **Backend findings (B12, 2026-11-05):**
  - **No code path sends a different token.** Each run reads `users.encrypted_github_token` once per user and decrypts it with the user's GitHub id as AAD; the calendar/insights fetch and the branch scan then use that same string. A decryption failure throws (it isn't a 401), and a 401 from the branch scan is only logged (its day is skipped, the user isn't flagged). The 03:00 line came from the calendar fetch.
  - **But the stored token could go back to an older one (B45, fixed here):** `Users` had no `@DynamicUpdate`, so every write of the row (`last_synced_at` after a sync, `needs_reauth`, preferences) wrote **every** column, the token included, as read at the start of that short transaction. A login committing in that window would have its new token overwritten by the old one. Also, a job that got a 401 on the token it had read flagged the row even if a login had stored a new token in the meantime. Neither needs a revocation, but both make the stored token older than the newest one, and that matters with the next point.
  - **A GitHub-side cause that fits the symptoms:** GitHub issues at most 10 tokens per user, OAuth app and scope set. The 11th login revokes the **oldest token**. The authorization stays, so there's no `oauth_authorization.destroy` in the security log. Every login stores a new token, so after ten or more logins to the production app (deploy testing counts), any older token that was still the stored one, or got written back by B45, gets a 401. Worth checking: how many times the developer logged in to the production app on 2026-11-04/05.
  - **Next occurrence:** every successful refresh (the hourly hero job's activity refresh and, since B11, the 03:00 daily insights refresh) now logs `token obtained <time>`, and a 401 logs GitHub's request id, its message and the token's time and age. If the 01:05 and 03:00 lines show the same time, GitHub rejected the token our app stored; if they differ, a login happened in between.

### B44
**Every context failed to start: a second constructor on the `AiProperties` record hid its binding constructor** · Phase 14 (B11) · Config · caught by `./mvnw verify` before the PR
- **Symptom:** every integration test failed with `Failed to instantiate [AiProperties]: No default constructor found`; all unit tests passed.
- **Root cause:** B11 added `regenerate` to the record and kept a 4-argument constructor so existing callers compile. Boot binds a `@ConfigurationProperties` record through its constructor only when there is exactly one; with two it falls back to JavaBean binding, which needs a no-argument constructor. The unit tests build the record by hand, so none of them went through the binder.
- **Fix:** `@ConstructorBinding` on the canonical (compact) constructor. `AiPropertiesTest` binds `insightshub.ai` from `application.yml` with Boot's `Binder`, so the next one fails in `./mvnw test`.
- **Lesson:** an extra constructor on a properties record needs `@ConstructorBinding` on the canonical one; test the binding itself, not only the record.

### B45
**A short `users` write could put back the GitHub token a login had just replaced, and a job's 401 could flag a user who had just logged in** · Phase 14 (B12, while looking into B43) · Concurrency · found by reading the code, reproduced in `it/GitHubTokenIT`
- **Symptom:** none seen yet. A row could keep an older GitHub token than the one the last login stored, or end up `needs_reauth` with a fresh, working token.
- **Root cause:**
  1. `Users` had no `@DynamicUpdate`, so Hibernate's UPDATE wrote every column. `InsightsWriter.save` (stamps `last_synced_at`), `markNeedsReauth` and `PreferencesService` load the row, change one field and commit, writing back the `encrypted_github_token` they had read. A login committing between that read and commit lost its new token. `GitHubTokenIT` fails exactly so without the annotation.
  2. `markNeedsReauth` flagged the row by id only. A job that read token A, started a slow GitHub call, and got a 401 on A would flag the user even if a login had stored token B in the meantime.
- **Fix:** `@DynamicUpdate` on `Users` (an UPDATE writes only the changed columns), and `UsersRepository.markNeedsReauth(id, rejectedToken)`, a compare-and-set on the encrypted token the job used (AES-GCM ciphertext is unique per login). A lost race is logged as "not flagged: a login stored a new token meanwhile".
- **Lesson:** an entity read-modify-write rewrites every column by default; on a row that another path writes (here, the login), use `@DynamicUpdate` or a targeted UPDATE, and make "mark it bad" conditional on the value that was found bad.
