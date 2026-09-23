# Implementation Plan: Job-Search CRM

## Overview

Ordered plan for building the Job-Search CRM in vertical slices. Each phase leaves the app in a coherent, testable state. Phases are prerequisite-ordered; within a phase, subtasks can often be parallelised.

Conventions:

- Every task ends with the tests appropriate to its layer (unit, property, repository, ArchUnit) per `.kiro/steering/development-guidelines.md`.
- No task is complete until it passes `./gradlew check`.
- Prefer vertical slices where practical: touch domain → persistence → application → CLI in the same task rather than building layers horizontally.

Design references throughout point to:

- `.kiro/steering/development-guidelines.md` (always-included steering)
- `.kiro/specs/job-search-crm/design.md`
- `.kiro/specs/job-search-crm/domain-model.md`
- `.kiro/specs/job-search-crm/agent-architecture.md`
- `.kiro/specs/job-search-crm/ui-cli.md`

---

## Tasks

### Phase 1: Bootstrap and Scaffolding

- [ ] 1. Set up Gradle project and base scaffolding
  - Initialise Gradle 8.x (Kotlin DSL) with Java 25 toolchain and Spring Boot 3.3.x
  - Add dependencies from `development-guidelines.md` (Spring Boot, Picocli + Spring starter, sqlite-jdbc, Flyway, Jackson, Google APIs, SLF4J/Logback)
  - Add test dependencies (JUnit 5, AssertJ, jqwik, Mockito, ArchUnit)
  - Configure Spotless with Google Java Style, 120 line length, no wildcard imports
  - Create the package skeleton: `core`, `messaging`, `agentic`, `ingest`, `rules`, `interfaces.cli`, `infrastructure` (with `persistence`, `migrations`, `llm`, `gmail`, `calendar`, `config`), and `app`
  - Add an `app.Main` with a Picocli root command that prints `jobcrm` help text
  - Add a smoke test that boots the Spring context without loading external services
  - _Design refs: development-guidelines.md, design.md § Package / Module Map_

- [ ] 2. Establish ArchUnit rules from day one
  - Enforce: `domain` has no imports outside `domain` (no Spring, Flyway, JDBC, Jackson)
  - Enforce: `interfaces.cli` never imports `domain` or `infrastructure` directly
  - Enforce: `infrastructure.persistence` classes implement interfaces from `domain`
  - Enforce: bounded-context dependency direction (`core` imports nothing from other contexts)
  - Enforce: no cycles between packages within a context
  - Add these to CI via `./gradlew check`
  - _Design refs: development-guidelines.md § DDD Conventions, Testing Conventions_

---

### Phase 2: Domain Foundation

- [ ] 3. Implement value objects and enums
  - Create strongly-typed IDs as records: `OpportunityId`, `ContactId`, `CompanyId`, `InteractionId`, `EventId`, `TaskId`, `DraftMessageId`, `DocumentId`, `AgentRunId`
  - Implement `EmailAddress` with RFC-5321-ish validation, canonical lower-case, `domain()` accessor
  - Implement enums: `Stage`, `Channel`, `Direction`, `ContactRole`, `TaskType`, `TaskStatus`, `EventKind`, `DraftStatus`, `DocumentKind`, `AgentRunStatus`, `AgentName`
  - Implement `Stage.canTransitionTo(next)` and `Stage.isTerminal()` per the state machine
  - Implement `ContactRef` and `TokenUsage` records
  - Unit tests: 100% branch coverage on `Stage.canTransitionTo` and `EmailAddress` validation
  - _Design refs: domain-model.md § Value Objects, § Stage Transition Matrix_

- [ ] 4. Implement core aggregates with in-memory persistence stubs
  - Implement `Company`, `Contact`, `Opportunity` aggregates with invariants enforced in mutators
  - Implement child entities: `Interaction`, `Event`, `Task`, `Document`
  - Implement `DraftMessage` aggregate with its state-machine transitions
  - Define repository interfaces in `domain`: `OpportunityRepository`, `ContactRepository`, `CompanyRepository`, `InteractionRepository`, `EventRepository`, `TaskRepository`, `DraftMessageRepository`
  - Provide in-memory implementations under `test` for unit-testing application logic later
  - Unit tests: aggregate invariants (illegal stage transitions throw, `attachContact` prevents duplicates, `DraftMessage.approve` respects state machine)
  - _Design refs: domain-model.md § Opportunity, § Contact, § Company, § Entities, § DraftMessage, § Repositories_

---

### Phase 3: Persistence

- [ ] 5. Wire SQLite with WAL, Hikari, and Flyway
  - Configure `SQLiteDataSource` with WAL mode enabled at startup
  - Configure two Hikari pools: write pool `maximumPoolSize=1`, read pool `maximumPoolSize=4`
  - Configure Flyway to run migrations at Spring context startup
  - Add a `SqliteBusyRetryInterceptor` (or JDBC template wrapper) that retries `SQLITE_BUSY` up to 3 times with jittered backoff
  - Startup test: context boots, migrations run, WAL is on
  - _Design refs: development-guidelines.md § Concurrency, design.md § Performance Considerations_

- [ ] 6. Write Flyway V1 migration for the core schema
  - Create `V1__init.sql` with all tables from `domain-model.md` § SQLite Schema (company, contact, contact_email, opportunity, opportunity_contact, interaction, interaction_contact, event, event_participant, task, draft_message, document, gmail_cursor, calendar_cursor)
  - Include all indexes and unique constraints
  - Repository test: apply the migration to a temp SQLite file and verify schema shape via `PRAGMA table_info`
  - _Design refs: domain-model.md § SQLite Schema, § Migration Strategy_

- [ ] 7. Implement JDBC repositories for core aggregates
  - Implement `JdbcCompanyRepository`, `JdbcContactRepository`, `JdbcOpportunityRepository`, `JdbcInteractionRepository`, `JdbcEventRepository`, `JdbcTaskRepository`, `JdbcDraftMessageRepository`
  - Use plain JDBC (no JPA); manual row mapping to keep domain types clean
  - Store UUIDs as TEXT, timestamps as ISO-8601 TEXT
  - `save` must handle both insert and update via upsert (`INSERT ... ON CONFLICT ... DO UPDATE`)
  - Repository tests: real SQLite temp file per test class, Flyway migrations in `@BeforeAll`; assert round-trip for every aggregate including collections (emails, contacts on an opportunity, participants on an event)
  - Property test: `save` then `findById` is a round-trip identity for every aggregate
  - _Design refs: development-guidelines.md § Testing Conventions § Repository tests_

---

### Phase 4: Application Services

- [ ] 8. Implement core application services with transactions
  - `OpportunityService`: `create`, `advanceStage` (throws `IllegalStageTransition`), `attachContact`, `recordInteraction`
  - `ContactService`: `createOrMerge` (dedup by canonical email), `rename`, `addEmail`
  - `CompanyService`: `create`, `renameTo`, `setDomain`, `findOrCreateByName`
  - `DraftReviewService`: `listPending`, `approve`, `discard`, `markSent`
  - Annotate with `@Transactional`; assert single-aggregate-per-transaction where practical
  - Unit tests with mocked repositories covering error paths (duplicate email, illegal transition, unknown id)
  - _Design refs: domain-model.md § Key Application-Service Signatures, development-guidelines.md § Error Handling_

---

### Phase 5: Base CLI Slice

- [ ] 9. Ship the first useful CLI slice (no LLM, no external services)
  - Wire `picocli-spring-boot-starter` so Picocli commands are Spring-managed
  - Implement global flags: `--json`, `--quiet`, `--verbose`, `--config`, `--help`
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

- [ ] 10. Configuration and logging plumbing
  - Implement `ConfigLoader` that reads packaged `application.yml`, overlays `~/.jobcrm/config.yml`, and applies `JOBCRM_*` environment overrides
  - Bind config to Spring `@ConfigurationProperties` records (never mutable POJOs)
  - Redact secret fields (`llm.api-key`, `gmail.client-secret`, `gmail.refresh-token`) in any logged config dump
  - Configure Logback with rolling file appender under `~/.jobcrm/logs/`, MDC keys `agentRunId`, `agentName`, `opportunityId`, `interactionId`
  - Add a `LogContext` helper to push/pop MDC keys safely
  - Unit tests: env vars override YAML; missing config falls back to defaults; secrets never appear in logged output
  - _Design refs: development-guidelines.md § Configuration and Secrets, § Logging Conventions_

- [ ] 11. Scheduling primitives (empty schedule)
  - Configure a single `ScheduledExecutorService` bean for periodic jobs
  - Configure `Executors.newVirtualThreadPerTaskExecutor()` bean for agent dispatch (used later)
  - Add a minimal `RulesEngine` class with a public `onTick(now)` method that does nothing yet
  - Integration test: scheduled executor is present, virtual-thread executor is present
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
