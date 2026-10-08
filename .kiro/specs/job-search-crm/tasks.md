# Implementation Plan: Job-Search CRM

## Overview

Ordered plan for building the Job-Search CRM in vertical slices. Each phase leaves the app in a coherent, testable state. Phases are prerequisite-ordered; within a phase, subtasks can often be parallelised.

Conventions:

- Every task ends with the tests appropriate to its layer (unit, property, repository, ArchUnit) per `.kiro/steering/development-guidelines.md`.
- No task is complete until it passes `./gradlew check`.
- Prefer vertical slices where practical: touch domain → persistence → application → CLI in the same task rather than building layers horizontally.
- When a task is completed, mark its checkbox `[x]` and append the merged PR link to the task line (e.g., `— [PR #7](...)`). This gives us a bidirectional trail between the plan and the code.

Design references throughout point to:

- `.kiro/steering/development-guidelines.md` (always-included steering)
- `.kiro/specs/job-search-crm/design.md`
- `.kiro/specs/job-search-crm/domain-model.md`
- `.kiro/specs/job-search-crm/agent-architecture.md`
- `.kiro/specs/job-search-crm/ui-cli.md`

---

## Tasks

### Phase 1: Bootstrap and Scaffolding

- [x] 1. Set up Gradle project and base scaffolding — **[PR #1](https://github.com/eraserof/job-search-crm/pull/1)** (merged)
  - Initialise Gradle 8.x (Kotlin DSL) with Java 25 toolchain and Spring Boot 3.3.x
  - Add dependencies from `development-guidelines.md` (Spring Boot, Picocli + Spring starter, sqlite-jdbc, Flyway, Jackson, Google APIs, SLF4J/Logback)
  - Add test dependencies (JUnit 5, AssertJ, jqwik, Mockito, ArchUnit)
  - Configure Spotless with Google Java Style, 120 line length, no wildcard imports
  - Create the package skeleton: `core`, `messaging`, `agentic`, `ingest`, `rules`, `interfaces.cli`, `infrastructure` (with `persistence`, `migrations`, `llm`, `gmail`, `calendar`, `config`), and `app`
  - Add an `app.Main` with a Picocli root command that prints `jobcrm` help text
  - Add a smoke test that boots the Spring context without loading external services
  - _Design refs: development-guidelines.md, design.md § Package / Module Map_

- [x] 2. Establish ArchUnit rules from day one — **[PR #2](https://github.com/eraserof/job-search-crm/pull/2)** (merged)
  - Enforce: `domain` has no imports outside `domain` (no Spring, Flyway, JDBC, Jackson)
  - Enforce: `interfaces.cli` never imports `domain` or `infrastructure` directly
  - Enforce: `infrastructure.persistence` classes implement interfaces from `domain`
  - Enforce: bounded-context dependency direction (`core` imports nothing from other contexts)
  - Enforce: no cycles between packages within a context
  - Add these to CI via `./gradlew check`
  - _Design refs: development-guidelines.md § DDD Conventions, Testing Conventions_

---

### Phase 2: Domain Foundation

- [x] 3. Implement value objects and enums — **[PR #3](https://github.com/eraserof/job-search-crm/pull/3)** (merged)
  - Create strongly-typed IDs as records: `OpportunityId`, `ContactId`, `CompanyId`, `InteractionId`, `EventId`, `TaskId`, `DraftMessageId`, `DocumentId`, `AgentRunId`
  - Implement `EmailAddress` with RFC-5321-ish validation, canonical lower-case, `domain()` accessor
  - Implement enums: `Stage`, `Channel`, `Direction`, `ContactRole`, `TaskType`, `TaskStatus`, `EventKind`, `DraftStatus`, `DocumentKind`, `AgentRunStatus`, `AgentName`
  - Implement `Stage.canTransitionTo(next)` and `Stage.isTerminal()` per the state machine
  - Implement `ContactRef` and `TokenUsage` records
  - Unit tests: 100% branch coverage on `Stage.canTransitionTo` and `EmailAddress` validation
  - _Design refs: domain-model.md § Value Objects, § Stage Transition Matrix_

- [x] 4. Implement core aggregates with in-memory persistence stubs — **[PR #4](https://github.com/eraserof/job-search-crm/pull/4)** (includes a follow-up refactor commit splitting `core.domain` into per-aggregate sub-packages: `company/`, `contact/`, `opportunity/`, `interaction/`, `event/`, `task/`, `document/`, `shared/`)
  - Implement `Company`, `Contact`, `Opportunity` aggregates with invariants enforced in mutators
  - Implement child entities: `Interaction`, `Event`, `Task`, `Document`
  - Implement `DraftMessage` aggregate with its state-machine transitions
  - Define repository interfaces in `domain`: `OpportunityRepository`, `ContactRepository`, `CompanyRepository`, `InteractionRepository`, `EventRepository`, `TaskRepository`, `DraftMessageRepository`
  - Provide in-memory implementations under `test` for unit-testing application logic later
  - Unit tests: aggregate invariants (illegal stage transitions throw, `attachContact` prevents duplicates, `DraftMessage.approve` respects state machine)
  - _Design refs: domain-model.md § Opportunity, § Contact, § Company, § Entities, § DraftMessage, § Repositories_

---

### Phase 3: Persistence

- [x] 5. Wire SQLite with WAL, Hikari, and Flyway — **[PR #6](https://github.com/eraserof/job-search-crm/pull/6)**
  - (done) Configure the datasource with WAL mode enabled — pragmas set via Hikari `connectionInitSql` (`journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`, `synchronous=NORMAL`) so they apply on every pooled connection
  - (done) Configure two Hikari pools: write pool `maximumPoolSize=1` (`writePool`), read pool `maximumPoolSize=4` (`readPool`)
  - (done) Configure Flyway to run migrations at Spring context startup — manual `Flyway` bean bound to the `writePool`, calls `.migrate()` on init
  - (done) Retry-on-`SQLITE_BUSY` wrapper — chose the **composition** approach (`WriteRetry` component + `SqlOperation<T>` functional interface) over an AOP interceptor or an abstract base class. Retries on codes 5 (BUSY) and 6 (LOCKED), rethrows anything else immediately, throws the last exception when attempts are exhausted. Attempt count is config-driven (`app.datasource.max-attempts`) via constructor injection so it's unit-testable without Spring.
  - (done) `WriteRetryTest` — three scenarios with attempt-count assertions: succeeds-after-2-retries (3 attempts), non-retryable-error-rethrown (stops as soon as a non-BUSY/LOCKED code is seen, without exhausting retries), exhausts-retries (3 attempts). Uses lambdas + `AtomicInteger`, no DB required.
  - _Moved to Task 6:_ WAL / migration startup verification test, and the checked→unchecked exception translation (`PersistenceException`). The decision landed as: log once and throw an unchecked `PersistenceException` wrapping the `SQLException`, so failures still surface to the CLI without forcing `throws` on intermediate layers.
  - _Deferred (still open, not blocking):_ application-level **jittered backoff** — current backoff is a small fixed millisecond step; `busy_timeout=5000` at the driver level already absorbs almost all contention for a single-user local app. Revisit if contention shows up.
  - _Design refs: development-guidelines.md § Concurrency, design.md § Performance Considerations_

- [x] 6. Write Flyway V1 migration for the core schema (+ items deferred from Task 5) — **[PR #8](https://github.com/eraserof/job-search-crm/pull/8)**
  - _Also fixed a production bug this task surfaced:_ Hikari `connectionInitSql` with a semicolon-joined pragma string only ran the **first** statement under sqlite-jdbc, so `foreign_keys=ON` silently never applied (FK cascades were dead). Switched `DataSourceConfig` to apply pragmas via `SQLiteConfig` data-source properties, which reliably sets all of them per connection. Guarded by `HikariInitSqlProbeTest`.
  - Create `V1__init.sql` with all tables from `domain-model.md` § SQLite Schema (company, contact, contact_email, opportunity, opportunity_contact, interaction, interaction_contact, event, event_participant, task, draft_message, document, gmail_cursor, calendar_cursor)
  - Include all indexes and unique constraints
  - Repository test: apply the migration to a temp SQLite file and verify schema shape via `PRAGMA table_info`
  - **Deferred from Task 5 — WAL / migration startup verification test**: against a real temp SQLite file, assert `PRAGMA journal_mode` returns `wal`, `PRAGMA foreign_keys` is on, and that Flyway actually ran `V1__init.sql` (migration history present + expected tables exist). Now implementable because `V1__init.sql` exists.
  - **Deferred from Task 5 — checked→unchecked exception translation in `WriteRetry`**:
    - Add an unchecked `PersistenceException extends RuntimeException` (in `infrastructure.persistence`) that wraps the originating `SQLException` as its cause.
    - When `WriteRetry.execute` gives up (non-retryable error, or retries exhausted), **log once** at the point of translation (SLF4J) and throw `PersistenceException` instead of the raw `SQLException`.
    - Rationale (confirmed with product intent): write failures must surface so the CLI can decide how to react (retry later, keep a local copy, discard). Unchecked propagation means intermediate layers (repositories, application services, domain) stay free of `throws SQLException` and of any `java.sql` reference — the failure flies straight to the CLI's single top-level handler, which is the deliberate catch point.
    - Update `SqlOperation` / `execute` so `execute` no longer declares `throws SQLException` (it throws unchecked). The `SqlOperation.run()` seam may still declare `throws SQLException` internally — that's fine, it's caught and translated inside `execute`.
    - Update `WriteRetryTest` accordingly: assert `execute` throws `PersistenceException` (not `SQLException`) on the non-retryable and exhausted-retries paths, and that the cause is the original `SQLException` with the expected error code. Keep the attempt-count assertions.
    - Log exactly once per genuine failure (at the translate point) to avoid duplicate log noise as it propagates.
  - _Design refs: domain-model.md § SQLite Schema, § Migration Strategy; development-guidelines.md § Error Handling, § Logging Conventions_

- [x] 7. Implement JDBC repositories for core aggregates — **[PR #9](https://github.com/eraserof/job-search-crm/pull/9)**
  - Implement `JdbcCompanyRepository`, `JdbcContactRepository`, `JdbcOpportunityRepository`, `JdbcInteractionRepository`, `JdbcEventRepository`, `JdbcTaskRepository`, `JdbcDraftMessageRepository`
  - _Chosen approach:_ raw JDBC + `WriteRetry` (not `JdbcTemplate`) — `JdbcTemplate` translates `SQLException` into Spring's `DataAccessException` before `WriteRetry` could inspect the SQLite error code, so the BUSY/LOCKED retry would never fire. Boilerplate is tamed by a shared `JdbcSupport` helper (`queryOne`/`queryList`/`update`/`inWriteTransaction`) and a `SqlTypes` conversion helper. Reads go through the read pool; writes through the write pool wrapped in `WriteRetry`.
  - _Schema fix (`V2__company_updated_at.sql`):_ the `Company` aggregate tracks `updatedAt` but V1's `company` table omitted the column (an inconsistency inherited from the design's schema). Added it additively with a default + backfill.
  - Use plain JDBC (no JPA); manual row mapping to keep domain types clean
  - Store UUIDs as TEXT, timestamps as ISO-8601 TEXT
  - `save` must handle both insert and update via upsert (`INSERT ... ON CONFLICT ... DO UPDATE`)
  - Repository tests: real SQLite temp file per test class, Flyway migrations in `@BeforeAll`; assert round-trip for every aggregate including collections (emails, contacts on an opportunity, participants on an event)
  - Property test: `save` then `findById` is a round-trip identity for every aggregate
  - _Design refs: development-guidelines.md § Testing Conventions § Repository tests_

---

### Phase 4: Application Services

- [x] 8. Implement core application services with transactions — **[PR #10](https://github.com/eraserof/job-search-crm/pull/10)**
  - `OpportunityService`: `create`, `advanceStage` (throws `IllegalStageTransition`), `attachContact`, `recordInteraction`
  - `ContactService`: `createOrMerge` (dedup by canonical email), `rename`, `addEmail`, `create`
  - `CompanyService`: `create`, `renameTo`, `setDomain`, `findOrCreateByName`
  - `DraftReviewService`: `listPending`, `approve`, `discard`, `markSent`
  - Unit tests (18) with mocked repositories cover the error paths: unknown id (`UnknownAggregate`/`UnknownDraft`), illegal stage transition, invalid draft transition, dedup-by-email, and no-save-on-failure.
  - _Transaction decision (not `@Transactional`):_ the repositories already own their write transactions via `JdbcSupport.inWriteTransaction` on the write pool. A Spring `@Transactional` on the service would open a separate transaction that doesn't wrap those hand-rolled JDBC transactions, so it would be misleading. Every service method mutates a single aggregate = one `repo.save` = already atomic. The one cross-aggregate op, `recordInteraction`, validates against the aggregate's invariants first (no write on failure), then writes the authoritative `Interaction` before the opportunity's derived view. Cross-aggregate consistency is eventual and self-healing: the opportunity reconstitutes its interaction-id list and `lastInteractionAt` from the interaction table on every load, so a partial failure is repaired on the next read. Outbox/domain-event upgrade path documented in `OpportunityService` for a future multi-user scenario.
  - Added a `Clock` bean (`app.ClockConfig`) injected into services so "now" is controllable (system UTC in prod, fixed clock in tests).
  - _Design refs: domain-model.md § Key Application-Service Signatures, development-guidelines.md § Error Handling_

---

### Phase 5: Base CLI Slice

- [x] 9. Ship the first useful CLI slice (no LLM, no external services) — **[PR #11](https://github.com/eraserof/job-search-crm/pull/11)**
  - Wire `picocli-spring-boot-starter` so Picocli commands are Spring-managed
  - Global flags via a `GlobalOptions` `@Mixin` (`--json`, `--quiet`, `--verbose`, `--config`). _Known limitation:_ flags are leaf-level, so they go after the subcommand (`jobcrm opp list --json`), not before it. Root-level global-flag propagation is deferred polish.
  - Added `findAll()` to `Company`/`Contact`/`Opportunity` repository ports (+ JDBC and in-memory impls) — the `list` commands genuinely needed it (chosen over faking it through search).
  - Shared CLI infra: `OutputRenderer` (table + pretty-JSON), `ShortRef` (first-8-char UUID prefix resolution with `NoSuchRef`/`AmbiguousRef`), `OutputFormat`.
  - Implement setup commands: `jobcrm init`, `jobcrm config get`, `jobcrm config set`, `jobcrm auth status` (report "not configured" for now)
  - Implement `jobcrm opp new`, `jobcrm opp list`, `jobcrm opp show`, `jobcrm opp advance`, `jobcrm opp attach-contact`
  - Implement `jobcrm contact new`, `jobcrm contact list`, `jobcrm contact show`, `jobcrm contact rename`, `jobcrm contact add-email`
  - Implement `jobcrm company new`, `jobcrm company list`, `jobcrm company show`, `jobcrm company set-domain`
  - Implement `jobcrm status <opportunityRef>` (deterministic; no agent)
  - Implement short-ref parsing (first 8 UUID chars → canonical UUID) with an ambiguity error
  - Implement table renderer and `--json` renderer for `list` and `show` commands
  - Smoke tests: `jobcrm init && jobcrm opp new --company Acme --role Engineer && jobcrm opp list` produces expected output
  - _Design refs: ui-cli.md § Command Surface, § Output Conventions_

---

### Phase 6: Configuration, Logging, Scheduling Scaffolding

- [x] 10. Configuration and logging plumbing — **[PR #12](https://github.com/eraserof/job-search-crm/pull/12)**
  - Bind config to Spring `@ConfigurationProperties` records (never mutable POJOs): `JobcrmConfig` with nested `Llm`/`Gmail`/`Calendar`/`Daemon` records, `@DefaultValue` on fields, activated by `@ConfigurationPropertiesScan` on `Main`.
  - Redact secret fields (`llm.api-key`, `gmail.client-secret`, `gmail.refresh-token`) in any logged config dump via `ConfigRedactor` (fixed mask, never a partial reveal; unset → `(unset)`).
  - Configure Logback (`logback-spring.xml`) with a console appender (System.err) and a daily rolling file appender under `~/.jobcrm/logs/`, surfacing MDC keys `agentRunId`, `agentName`, `opportunityId`, `interactionId` via `%mdc`.
  - Add a `LogContext` helper: `AutoCloseable` push/pop of MDC keys, restoring prior values (not just removing) so nested contexts are correct.
  - Wired `jobcrm config get [<key>]` to the bound config (secrets masked on display); `config set` is deferred (atomic comment-preserving YAML editing of the overlay is its own focused piece of work — users edit `~/.jobcrm/config.yml` or set `JOBCRM_*` env vars for now).
  - _Approach note (no hand-rolled `ConfigLoader`):_ the overlay + env-override precedence the task asked for is exactly Spring Boot's property-source model, so instead of a bespoke loader we use `spring.config.import: "optional:file:${user.home}/.jobcrm/config.yml"` for the overlay and relaxed binding for `JOBCRM_*` env vars. One fewer moving part, and precedence is the framework's well-tested behavior rather than ours.
  - _Singleton-command fix:_ `config get`'s optional `<key>` positional leaked across invocations because Picocli command beans are Spring singletons; made the `Get` subcommand `@Scope("prototype")` so each invocation gets a fresh instance.
  - Unit tests (210 total): `JobcrmConfigTest` proves property override wins over YAML and missing config falls back to defaults; `ConfigRedactorTest` proves secrets never appear verbatim; `LogContextTest` proves MDC set/restore including nested contexts; `CliSmokeTest` exercises `config get` end-to-end (reads defaults, masks secrets, rejects unknown keys).
  - _Design refs: development-guidelines.md § Configuration and Secrets, § Logging Conventions_

- [x] 11. Scheduling primitives (empty schedule) — **[PR #13](https://github.com/eraserof/job-search-crm/pull/13)**
  - `SchedulingConfig` (`com.jobcrm.app`) exposes two executor beans built from JDK static factories (same pattern as `ClockConfig`/`DataSourceConfig`), both `@Bean(destroyMethod = "shutdown")` so the context closes them cleanly on CLI exit:
    - `@Qualifier("scheduler")` — a `ScheduledExecutorService` (`newScheduledThreadPool(4)`) for periodic jobs. Sized at 4 so a slow poller can't delay the other periodic jobs that fire near the same instant.
    - `@Qualifier("agentDispatcher")` — a virtual-thread `ExecutorService` (`newVirtualThreadPerTaskExecutor()`) for agent dispatch. Spawn-per-task, not a reused fixed pool: agent work is I/O-bound (blocking on the LLM), which is exactly what virtual threads are for.
  - `RulesEngine` (`com.jobcrm.rules`, `@Component`) with an empty `onTick(Instant now)` stub. `now` is passed in (not read from a clock inside) to keep the eventual rules deterministic and testable; real `onCalendarSyncTick`/`nightlyStaleSweep` logic lands in Tasks 26-27.
  - _Nothing is scheduled yet_ — this is the "empty schedule" skeleton. Wiring `scheduler → rulesEngine.onTick(clock.instant())` happens with the daemon (Task 28).
  - Integration test (`SchedulingConfigTest`, 5 tests): both executor beans and the `RulesEngine` are present; each executor actually runs a submitted task; the dispatcher runs on a virtual thread (proving it's the right bean, not just any `ExecutorService`); `onTick` is callable without throwing.
  - _Design refs: development-guidelines.md § Concurrency, design.md § Rules Engine_

---

### Phase 7: Agent Runtime Foundation

- [ ] 12. AgentRun aggregate and V2 migration
  - Implement `AgentRun` aggregate with append-only `AgentTurn` sealed hierarchy (`Assistant`, `ToolResultTurn`, `System`, `User`)
  - Write `V2__agent_runtime.sql` with `agent_run` and `agent_turn` tables per `agent-architecture.md`
  - Implement `JdbcAgentRunRepository` with round-trip tests
  - Property test: appended turns preserve order; `AgentRun` cannot be mutated after `finish`/`fail`/`abort`
  - _Design refs: agent-architecture.md § AgentRun, § AgentRun schema_

- [ ] 13. LlmClient port and provider implementations
  - Define `LlmClient`, `LlmRequest`, `LlmResponse`, `LlmMessage`, `ToolSpec`, `ToolCall`, `LlmModelParams`, `TokenUsage`, `Role` in `agentic.domain`
  - Implement `AnthropicLlmClient` under `infrastructure.llm` using JDK `HttpClient`; support tool-use turns per the Anthropic Messages API
  - Implement `MockLlmClient` (Mockito-friendly) that returns pre-programmed responses
  - Implement `RecordedLlmClient` that reads a fixture JSON and returns responses keyed by turn index
  - Implement `LlmClientFactory` that resolves an `LlmClient` from `(provider, model, params)` config
  - Unit tests: `MockLlmClient` returns programmed responses; `RecordedLlmClient` throws when the fixture runs out
  - _Design refs: agent-architecture.md § LlmClient_

- [ ] 14. Tool registry and agent runner
  - Define `AgentTool`, `ToolResult`, `ToolContext`, `ToolRegistry`
  - Implement Spring-based `ToolRegistry` that discovers `AgentTool` beans by `name()`
  - Implement `AgentDefinition` interface and `AgentRunner` per the pseudocode in `agent-architecture.md`
  - Implement `AgentDispatcher` with per-agent `Semaphore(1)` mutex and virtual-thread executor; expose `dispatch` (fire-and-forget) and `dispatchSync`
  - Unit tests (with `MockLlmClient`): honours `maxSteps`, refuses off-list tools with `policy_violation`, retries synthetic invalid-args up to 3× then fails, records every turn to `AgentRun`, accumulates tokens correctly
  - Property test (jqwik): every runner outcome is one of `Success | Failed | Aborted`; turn count in persisted `AgentRun` equals stub-requested count
  - _Design refs: agent-architecture.md § Tool Contract and Registry, § AgentDefinition and AgentRunner, § Execution Loop_

---

### Phase 8: Tools

- [ ] 15. Read tools
  - Implement: `searchOpportunities`, `searchOpportunitiesByCompany`, `searchOpportunitiesByContact`, `readOpportunity`, `readContact`, `searchContacts`, `findCompanyByDomain`, `listInteractions`, `listInteractionsByThreadKey`, `listRecentUserSentMessages`, `listEvents`, `listOpenTasks`, `findSilentOpportunities`
  - Each tool exposes a JSON schema and validates inputs (use `com.networknt:json-schema-validator` or equivalent)
  - All list-returning tools default to `limit=25`
  - Unit tests: schema rejects malformed args; each tool returns the expected shape; empty result sets serialize as `{"results": []}` rather than `null`
  - _Design refs: agent-architecture.md § v1 Tool Catalog_

- [ ] 16. Write tools
  - Implement: `createOpportunity`, `createContact`, `attachInteraction`, `advanceStage`, `createTask`, `createDraftMessage`
  - `attachInteraction` is idempotent via `externalId` (email/LI) or `(opportunityId, occurredAt, first 64 chars of body)` (call notes)
  - `advanceStage` fails loudly on illegal transitions and returns a helpful error
  - Explicitly do not register a `sendMessage` tool; add an ArchUnit test asserting no bean is named `sendMessage`
  - Unit + integration tests: each tool round-trips through a real SQLite temp file; idempotency verified by calling twice
  - _Design refs: agent-architecture.md § v1 Tool Catalog; correctness property "No autonomous send"_

---

### Phase 9: Agents Without External Integrations

- [ ] 17. QueryAgent
  - Define system prompt (grounding preamble + query-specific rules)
  - Register `AgentDefinition` bean with allow-listed tools from `agent-architecture.md`
  - Fixture-based integration tests: canned questions ("silent loops 2+ weeks", "loops in PANEL stage", "when did I last hear from X?") produce grounded answers referencing opportunity IDs
  - _Design refs: agent-architecture.md § Agent Roster, § Prompt Principles_

- [ ] 18. InterviewPrepAgent
  - Define system prompt and allow-list
  - Fixture tests: given a sample opportunity with 5 prior interactions and an upcoming interview event, produce a briefing that references named contacts and prior statements
  - _Design refs: agent-architecture.md § Agent Roster_

- [ ] 19. DraftReplyAgent
  - Define system prompt with voice-matching guidance and 150-word soft cap
  - Allow-list: `readOpportunity`, `listInteractions`, `listRecentUserSentMessages`, `createDraftMessage`
  - Fixture tests: given a Task and a set of user's prior sent messages, produce a draft that (a) uses only greetings the user has used before, (b) references specific facts from the thread, (c) creates a `DraftMessage` in `PENDING_REVIEW`
  - _Design refs: agent-architecture.md § Agent Roster, § Prompt Principles_

- [ ] 20. StaleDetectAgent
  - Define system prompt, allow-list, and nightly-trigger contract
  - Fixture tests: given a set of silent opportunities and prior interactions, produce nudge tasks with commentary referencing specific prior promises
  - _Design refs: agent-architecture.md § Agent Roster_

---

### Phase 10: CLI Expansion for Agent Workflows

- [ ] 21. CLI commands for tasks, drafts, and queries
  - Implement `jobcrm today` (aggregation of due tasks, upcoming interviews, pending drafts, silent-loop count — deterministic, no agent)
  - Implement `jobcrm tasks list`, `jobcrm tasks done`, `jobcrm tasks dismiss`
  - Implement `jobcrm drafts list`, `jobcrm drafts show`, `jobcrm drafts edit` (opens `$EDITOR`), `jobcrm drafts approve`, `jobcrm drafts discard`, `jobcrm drafts mark-sent`
  - Implement `jobcrm draft <taskRef>` (dispatch `DraftReplyAgent` synchronously with progress line + `--async` flag)
  - Implement `jobcrm ask "<question>"` (dispatch `QueryAgent`) and `jobcrm prep <ref>` (dispatch `InterviewPrepAgent`)
  - Implement `jobcrm runs list`, `jobcrm runs show`, `jobcrm runs replay`
  - Implement `jobcrm dev record-agent <name> <scenario>` for capturing new fixtures
  - Smoke tests through Picocli's test harness
  - _Design refs: ui-cli.md § Command Surface, § Draft Review Flow_

---

### Phase 11: Manual Paste Ingestion

- [ ] 22. Call-note and LinkedIn ingestion agents + CLI
  - Implement `CallNoteIngestAgent` and `LinkedInIngestAgent` with prompts and allow-lists per `agent-architecture.md`
  - Implement `jobcrm log call` and `jobcrm log linkedin`: prompt for body via `$EDITOR` or accept `--body` inline / `--body -` from stdin
  - `log call` accepts `--opportunity`, `--with <contactRef>...`, `--at <ISO-instant>`; `log linkedin` optionally accepts `--opportunity`
  - Fixture tests plus CLI smoke tests
  - _Design refs: ui-cli.md § Ingestion Flow Summary, agent-architecture.md § Agent Roster_

---

### Phase 12: Gmail Integration

- [ ] 23. Gmail OAuth and adapter
  - Implement `GmailAdapter` port with methods `startAuthFlow`, `completeAuthFlow`, `pullDelta(historyId?)`, `fetchMessage(id)`
  - Implement `GoogleGmailAdapter` in `infrastructure.gmail` using `google-api-services-gmail` with the installed-app OAuth flow
  - Persist the refresh token to `~/.jobcrm/config.yml` via `ConfigLoader.write`
  - Implement `jobcrm auth gmail` (interactive) and `jobcrm auth gmail --revoke`
  - Unit tests using a fake `GmailAdapter` that returns canned deltas
  - _Design refs: ui-cli.md § Setup and auth, design.md § Security Considerations_

- [ ] 24. Gmail poller and EmailIngestAgent
  - Implement `GmailPoller` scheduled every 5 minutes; advances `gmail_cursor.history_id` on success
  - Implement `EmailIngestAppService.ingest(RawEmail)` with idempotency by `messageId` (`Interaction.externalId` unique)
  - Implement `EmailIngestAgent` with the prompt and allow-list from `agent-architecture.md`
  - Implement `jobcrm sync-gmail` (one-shot poll, safe alongside daemon)
  - Fixture tests covering: exact-thread match, contact-match with single opportunity, company-domain match, ambiguous multi-opportunity disambiguation, no-match producing an ambiguity Task
  - Property test: idempotent ingest — calling `ingest(e)` N times results in exactly one `Interaction`
  - _Design refs: agent-architecture.md § Email → Opportunity Matching; correctness property "Idempotent email ingest"_

---

### Phase 13: Calendar Integration and Post-Interview Rule

- [ ] 25. Calendar sync
  - Implement `CalendarAdapter` port with `pullSince(syncToken?)` and `fetchEvent(id)`
  - Implement `GoogleCalendarAdapter` under `infrastructure.calendar`
  - Implement `CalendarSync` scheduled every 5 minutes; upserts `Event` rows keyed by `calendar_event_id`; advances `calendar_cursor.sync_token`
  - Unit tests using a fake adapter returning canned events
  - _Design refs: design.md § High-Level Component Diagram, § Rules Engine_

- [ ] 26. Post-interview follow-up rule
  - Implement `RulesEngine.onCalendarSyncTick(now)` per the pseudocode in `design.md`
  - For each interview/onsite event ended in the previous window: create one open `THANK_YOU` Task per participant with `dueAt = endsAt + 24h`, guarded against duplicates
  - Pre-warm `DraftReplyAgent` for each new task
  - Property test: for every ended interview, exactly one open `THANK_YOU` per participant after a tick; ticking twice never creates duplicates
  - _Design refs: design.md § Rules Engine; correctness property "Post-interview task creation is idempotent"_

- [ ] 27. Nightly stale-loop sweep
  - Implement `RulesEngine.nightlyStaleSweep(now)` per pseudocode
  - Wire it into the scheduler at the configured `daemon.stale-sweep-time`
  - Property test: no `NUDGE` duplicates; terminal-stage opportunities are skipped
  - _Design refs: design.md § Rules Engine, § Nightly stale-loop sweep_

---

### Phase 14: Daemon

- [ ] 28. Daemon lifecycle and CLI
  - Implement `jobcrm daemon start` (foreground; `--detach` on Windows uses a helper) that acquires a filesystem lock at `~/.jobcrm/daemon.lock`
  - Implement `jobcrm daemon stop` (writes a stop signal file the daemon watches)
  - Implement `jobcrm daemon status` (reports running, PID, uptime)
  - Implement `jobcrm daemon logs [--tail N]`
  - Ensure schedulers only run when the daemon is up; one-shot commands never start them
  - Integration test: start daemon in a thread, hit `status`, then `stop`, verify clean shutdown and lock release
  - _Design refs: ui-cli.md § Daemon Model, § Daemon lifecycle commands_

- [ ] 29. Daily briefing job
  - Implement a scheduled job at the configured `daemon.briefing-time` that dispatches a briefing agent (reuse `QueryAgent` with a fixed input, or add a dedicated `DailyBriefingAgent`) and stores the result for `jobcrm today` to render
  - Cache the briefing until the next scheduled run
  - _Design refs: ui-cli.md § Daemon Model, § Command Surface_

---

### Phase 15: Cross-Cutting Tests and Packaging

- [ ] 30. Property-based test suite
  - Add jqwik properties for every "Correctness Properties" entry in `domain-model.md`, `agent-architecture.md`, and `design.md`:
    - Idempotent email ingest
    - Stage-transition safety
    - Draft state machine reachability
    - Contact email uniqueness
    - Interaction integrity
    - Idempotent externalId
    - Task–draft coupling
    - Bounded agent runs
    - Tool allow-list enforcement
    - No autonomous send tool exists
    - Agent grounding audit trail (every domain change during an agent run has a linked tool-call turn)
    - Post-interview task creation
  - _Design refs: domain-model.md § Correctness Properties (Domain), agent-architecture.md § Correctness Properties (Agent Runtime), design.md § Correctness Properties_

- [ ] 31. End-to-end integration tests
  - Test 1: email arrives via fake Gmail adapter → `EmailIngestAgent` runs (fixture) → `Interaction` persisted → `Task` created → `DraftReplyAgent` dispatched → `DraftMessage` in `PENDING_REVIEW`
  - Test 2: calendar event ends → post-interview rule fires → `THANK_YOU` task exists → CLI `jobcrm today` shows it
  - Test 3: `jobcrm ask "..."` returns a grounded answer with opportunity IDs referenced
  - Test 4: `jobcrm drafts approve` transitions state; `mark-sent` transitions to `SENT`
  - _Design refs: development-guidelines.md § Testing Conventions § Integration tests, design.md § Key Sequence Flows_

- [ ] 32. Package and document
  - `./gradlew bootJar` produces a runnable `jobcrm.jar`
  - Ship wrapper scripts: `jobcrm` (bash) and `jobcrm.bat` (Windows cmd) that resolve `java -jar` with the packaged JAR
  - Write `README.md` quickstart: install Java 25, `jobcrm init`, `jobcrm auth gmail`, `jobcrm auth llm --set-key`, `jobcrm daemon start`, first `jobcrm today`
  - Document the outbound-traffic surface (Anthropic, Gmail, Calendar) and the sensitive config location
  - Add a `CHANGELOG.md` starting at 0.1.0
  - _Design refs: design.md § Deployment, § Security Considerations_

---

## Task Dependency Graph

```
Phase 1 (Bootstrap) ──▶ Phase 2 (Domain foundation) ──▶ Phase 3 (Persistence)
                                                              │
                                                              ▼
                                                       Phase 4 (Application services)
                                                              │
                                                              ▼
                                                       Phase 5 (Base CLI slice)
                                                              │
                                                              ▼
                                                       Phase 6 (Config/logging/scheduling)
                                                              │
                                                              ▼
                                                       Phase 7 (Agent runtime foundation)
                                                              │
                                                              ▼
                                                       Phase 8 (Tools)
                                                              │
                                                              ▼
                                                       Phase 9 (Non-external agents)
                                                              │
                                                              ▼
                                                       Phase 10 (CLI for agent workflows)
                                                          ┌───┴───┐
                                                          ▼       ▼
                                                Phase 11         Phase 12
                                              (Manual paste)   (Gmail integration)
                                                                  │
                                                                  ▼
                                                             Phase 13 (Calendar + rules)
                                                                  │
                                                                  ▼
                                                             Phase 14 (Daemon)
                                                                  │
                                                                  ▼
                                                             Phase 15 (Tests + packaging)
```

Waves indicate tasks that can be worked on in parallel once the previous wave is complete.

```json
{
  "waves": [
    { "wave": 1,  "description": "Bootstrap",                          "tasks": [1, 2] },
    { "wave": 2,  "description": "Domain foundation",                  "tasks": [3, 4] },
    { "wave": 3,  "description": "Persistence infrastructure",         "tasks": [5, 6] },
    { "wave": 4,  "description": "JDBC repositories",                  "tasks": [7] },
    { "wave": 5,  "description": "Application services and base CLI", "tasks": [8, 9] },
    { "wave": 6,  "description": "Config, logging, scheduling",        "tasks": [10, 11] },
    { "wave": 7,  "description": "AgentRun aggregate and LlmClient",   "tasks": [12, 13] },
    { "wave": 8,  "description": "Tool registry and agent runner",     "tasks": [14] },
    { "wave": 9,  "description": "Tools",                              "tasks": [15, 16] },
    { "wave": 10, "description": "Non-external agents",                "tasks": [17, 18, 19, 20] },
    { "wave": 11, "description": "CLI expansion for agent workflows",  "tasks": [21] },
    { "wave": 12, "description": "Manual ingestion + Gmail auth",      "tasks": [22, 23] },
    { "wave": 13, "description": "Gmail poller + calendar sync",       "tasks": [24, 25] },
    { "wave": 14, "description": "Rules engine",                       "tasks": [26, 27] },
    { "wave": 15, "description": "Daemon and briefing",                "tasks": [28, 29] },
    { "wave": 16, "description": "Cross-cutting tests and packaging",  "tasks": [30, 31, 32] }
  ]
}
```

Task-level dependencies within each phase:

- 1 → 2 (ArchUnit rules need packages to exist)
- 3 → 4 (aggregates use value objects)
- 5 → 6 → 7 (Flyway config, migration file, then JDBC impls)
- 8 depends on 7 (services need repositories)
- 9 depends on 8 (CLI hits app services)
- 10 → 11 (scheduling primitives need config loader wired)
- 12 → 13 → 14 (AgentRun before LlmClient before runner)
- 15 → 16 (write tools may depend on read tools for validation lookups)
- 17–20 all depend on 14 and 16 but are independent of each other
- 21 depends on 17–20 being in place
- 22 depends on 14, 16 (agents 22 are new agents wired the same way)
- 23 → 24 (poller needs adapter and OAuth)
- 25 → 26 → 27 (calendar adapter before rule; sweep is independent but reuses scheduling)
- 28, 29 depend on 24, 26, 27 to have real work to schedule
- 30–32 run last

---

## Notes

### Non-Goals for v1 (explicit)

The following are intentionally out of scope. Do not build them without an explicit spec update.

- Any web UI, desktop UI, or mobile client.
- Autonomous message sending (no `sendMessage` tool).
- LinkedIn API integration or browser scraping.
- Multi-user support or network deployment.
- App-level encryption of secrets (rely on OS filesystem permissions and disk encryption).
- Native launcher via jlink/jpackage.

### Ordering Notes

- Phases 1–5 are strict prerequisites for everything else and must be built in order.
- Phase 6 (config/logging/scheduling) is a soft prerequisite for Phase 7 onward.
- Phases 7–8 (agent runtime + tools) unlock Phase 9 (non-external agents), which unlocks Phase 10 (CLI expansion).
- Phase 11 (manual ingestion) is independent of Phase 12 (Gmail) — they can go in either order once Phases 7–10 are done.
- Phase 14 (daemon) is best done after Phases 12–13 exist so the daemon has real work to schedule.
- Phase 15 is continuous — property tests get added as their targets are built. The tasks in Phase 15 represent the final consolidation pass.

### Definition of Done (per task)

Every task is complete only when:

1. Code compiles under Java 25 with Spotless clean.
2. All new tests pass under `./gradlew check`.
3. ArchUnit rules still pass (no package-dependency violations introduced).
4. No `System.out`; logs go through SLF4J.
5. Any new configuration keys are documented in `.kiro/specs/job-search-crm/ui-cli.md` § Configuration File.
