---
inclusion: always
---

# Development Guidelines

Team-wide conventions for building the Job-Search CRM. This document is the source of truth for language, structure, testing, and dependencies. Anything not covered here is decided by the design docs under `.kiro/specs/job-search-crm/`.

---

## Language and Toolchain

| Item | Choice |
|------|--------|
| Language | Java |
| JDK | Java 25 (current LTS, released Sept 2025) |
| Build tool | Gradle (Kotlin DSL) |
| Framework backbone | Spring Boot 3.3.x (used for DI, config, scheduling — **not** for a web UI) |
| CLI framework | Picocli |
| Database | SQLite (single file, WAL mode) |
| Migrations | Flyway |
| JDBC pool | HikariCP (write pool sized at 1; read pool up to 4) |
| Logging | SLF4J + Logback |
| JSON | Jackson |
| HTTP client | Java's built-in `java.net.http.HttpClient` |

Use records, sealed interfaces, pattern matching, and virtual threads liberally — they map cleanly to DDD value objects, aggregates, and the agent runtime.

---

## DDD Conventions

### Layer boundaries

Four layers, strictly one-way dependencies (downward only):

```
interfaces/  (Picocli commands, controllers)
    │
    ▼
application/  (use-case services, transaction boundaries, dispatchers)
    │
    ▼
domain/  (aggregates, entities, value objects, repository interfaces, domain services)
    │
    ▼
infrastructure/  (JDBC repos, LLM client impls, Gmail adapter, config loader)
```

Rules:

- `domain` depends on nothing outside `domain`. No Spring annotations, no Jackson annotations, no Flyway, no JDBC. Pure Java.
- `application` depends on `domain` interfaces and orchestrates use cases. Transactions start and end here.
- `infrastructure` implements `domain` interfaces (repositories, ports). Framework dependencies (Spring, Flyway, JDBC drivers) live here.
- `interfaces` calls `application`. Never `domain` directly.

Violations get caught by ArchUnit tests (see Testing section).

### Aggregate rules

- Every aggregate has one root entity and one repository.
- Aggregate roots enforce their own invariants inside setter/mutation methods. No public setters that bypass invariants.
- One transaction touches one aggregate at a time when practical. Cross-aggregate consistency is eventual, mediated by application services.
- Child entities of an aggregate are only accessible through the root. External code never holds direct references to child entities across a transaction boundary.
- Identifiers are strongly-typed value objects (`OpportunityId`, `ContactId`, etc.), not raw `UUID` or `String`.

### Value objects

- Prefer records for value objects.
- Validate in the compact constructor. Invalid states are unrepresentable.
- Value objects are immutable, self-contained, and equality-by-value.
- Enums for closed sets (Stage, Channel, ContactRole). Include behavior on the enum where it belongs (e.g., `Stage.canTransitionTo`).

### Ports and adapters

Every external dependency (LLM provider, Gmail, calendar, filesystem-for-documents) is behind an interface defined in `domain` or `application` and implemented in `infrastructure`. This is what makes swapping implementations by config a one-line change.

---

## Package Structure

Package-by-context, not package-by-layer at the top level. Within a context, split by layer.

```
com.jobcrm
├── core                   // "Job Search Core" bounded context
│   ├── domain
│   ├── application
│   └── (infra lives under infrastructure/persistence, but registered per-context)
├── messaging              // Draft & outbox bounded context
│   ├── domain
│   └── application
├── agentic                // Agent runtime bounded context
│   ├── domain
│   ├── application
│   └── agents             // Concrete AgentDefinitions
├── ingest                 // Gmail / calendar / manual bounded context
│   ├── application
│   └── infrastructure
├── rules                  // Deterministic scheduling and rule execution
├── interfaces
│   └── cli                // Picocli commands
├── infrastructure
│   ├── persistence        // JDBC repo implementations
│   ├── migrations         // Flyway SQL files
│   ├── llm                // LlmClient implementations (Anthropic, mock)
│   ├── gmail              // Gmail adapter
│   ├── calendar           // Google Calendar adapter
│   └── config             // Config loader, secrets loader
└── app                    // Composition root, main(), Spring configuration
```

---

## Testing Conventions

Test framework: JUnit 5 + AssertJ. Property tests: jqwik. Mocking: Mockito. Architecture rules: ArchUnit.

### Unit tests

- Domain tests are pure — no Spring, no I/O. Fast, deterministic.
- Aim for 100% branch coverage on domain state machines (`Stage.canTransitionTo`, `DraftStatus` transitions).
- Application services are tested with mocked repositories and mocked adapters.

### Property-based tests

Cover the correctness properties enumerated in `.kiro/specs/job-search-crm/domain-model.md` and `agent-architecture.md`. Examples:

- Idempotent email ingest
- Stage transitions match the state machine
- Draft state machine reachability
- Contact email uniqueness

### LLM testing — dual strategy

Because LLMs are non-deterministic and slow, we use **two** test approaches:

1. **Mockito mocks** for unit-level agent tests. Stub `LlmClient` to return canned `LlmResponse` values. Fast, deterministic, used for verifying agent-loop control flow (tool allow-list enforcement, step cap, error paths, retry-on-invalid-args).
2. **Recorded fixtures** for integration tests where response shape matters. Real LLM calls are captured once as JSON under `src/test/resources/fixtures/agents/<agentName>/<scenario>.json` and replayed via `RecordedLlmClient`. When prompts change, fixtures are re-recorded intentionally.

Rules:

- Never call a real LLM API from CI. Recording is a manual developer action.
- **Committed fixtures use synthetic data only.** When recording a fixture against real inbox data (real names, real companies, real emails), do not commit it. Sanitise or regenerate against a synthetic dataset before committing. Files matching `*.private.json` are gitignored to catch accidental commits.

### Repository tests

- Use a real SQLite instance per test class in an OS temp file. Migrations run in `@BeforeAll`.
- No in-memory H2 or other DB substitutes — SQLite behavior (WAL, `SQLITE_BUSY`, TEXT type affinity) matters.

### Architecture tests

Use ArchUnit to enforce:

- No cycle between packages within a context.
- `domain` depends on nothing outside `domain`.
- `interfaces.cli` never imports `domain` or `infrastructure` directly.
- Repositories under `infrastructure.persistence` implement interfaces from `domain`.

### Integration tests

- Full Spring context, in-memory temp SQLite.
- Fake `GmailAdapter` and fake `CalendarAdapter` return canned payloads.
- Mocked or recorded `LlmClient`.
- Assert end-to-end: input event → domain state change → dispatched agent run → persisted draft/task.

---

## Configuration and Secrets

- Application defaults live in packaged `application.yml`.
- User overrides live in `~/.jobcrm/config.yml` (Linux/macOS) or `%USERPROFILE%\.jobcrm\config.yml` (Windows).
- Environment variables override YAML: `JOBCRM_LLM_API_KEY`, `JOBCRM_GMAIL_CLIENT_ID`, etc.
- Secrets are stored in plain YAML in the user config directory for v1. The directory is documented as sensitive; rely on OS filesystem permissions and disk encryption. **No app-level encryption in v1.** Keychain integration is a documented future step, not a blocker.
- Never log secret values. Never send secrets to the LLM. Redact tokens in error messages.

---

## Logging Conventions

- SLF4J only; never `System.out`.
- Log levels:
  - `ERROR`: unrecoverable, needs user attention (auth expired, DB corrupt).
  - `WARN`: recoverable but abnormal (LLM 5xx, `SQLITE_BUSY` retry).
  - `INFO`: lifecycle events (daemon start, agent run started/finished, Gmail poll cursor advance).
  - `DEBUG`: per-tool-call, per-LLM-turn detail.
- Structured logging: use MDC keys `agentRunId`, `agentName`, `opportunityId`, `interactionId` where relevant.
- The `AgentRun` audit trail in the database is the primary observability tool. Logs are secondary.
- Log files: rotated daily under `~/.jobcrm/logs/`.

---

## Concurrency

- Java 21+ virtual threads for agent dispatch (`Executors.newVirtualThreadPerTaskExecutor()`).
- One `ScheduledExecutorService` for periodic jobs (Gmail poller, calendar sync, stale sweep, daily briefing).
- **Per-agent mutex**: `Map<AgentName, Semaphore(1)>` prevents concurrent runs of the same agent. Different agents run in parallel.
- SQLite writes serialize on a size-1 Hikari pool; reads use a size-4 pool. WAL mode enabled at startup.
- No shared mutable state outside repositories. Application services are stateless.

---

## Error Handling

- Domain layer throws domain-specific unchecked exceptions (`IllegalStageTransition`, `DuplicateContactEmail`, etc.). Never `RuntimeException` bare.
- Application layer catches domain exceptions and translates to result objects (`IngestResult.duplicate`, `IngestResult.enqueued`) when the caller needs to distinguish outcomes.
- Infrastructure layer catches provider exceptions (Gmail API errors, LLM 5xx) and translates to explicit failure modes at the port boundary. Never let raw provider exceptions leak upward.
- CLI catches everything and prints a user-friendly message plus an `agentRunId` or log pointer for debugging.

---

## Dependency Direction Between Bounded Contexts

```
ingest        →  core, agentic
agentic       →  core, messaging
messaging     →  core
rules         →  core, agentic
core          →  (nothing)
```

`core` never imports from any other context. Any cross-context read from `core` happens through repository interfaces `core` owns. Any write to `core` happens through application services in `core` that other contexts call.

---

## Dependencies

### Runtime

| Purpose | Library | Version |
|---------|---------|---------|
| Framework | Spring Boot | 3.3.x |
| CLI | Picocli | 4.7.x |
| Picocli–Spring bridge | picocli-spring-boot-starter | 4.7.x |
| DB driver | org.xerial:sqlite-jdbc | 3.46.x |
| Connection pool | HikariCP | (Spring Boot) |
| Migrations | Flyway (Community) | 10.x |
| HTTP client | JDK `java.net.http.HttpClient` | JDK |
| JSON | Jackson | (Spring Boot) |
| Google APIs | google-api-client, google-api-services-gmail, google-api-services-calendar | latest |
| Logging | SLF4J + Logback | (Spring Boot) |

### Build / Test

| Purpose | Library |
|---------|---------|
| Build tool | Gradle 8.x (Kotlin DSL) |
| JVM | JDK 25 |
| Unit test | JUnit 5 |
| Assertions | AssertJ |
| Property tests | jqwik |
| Mocking | Mockito |
| Arch rules | ArchUnit |

### Explicitly Not Included in v1

- BouncyCastle (no app-level secret encryption in v1).
- Any web-UI framework (Thymeleaf, React, etc.).
- Any LinkedIn API SDK (LinkedIn ingestion is manual-paste only).

---

## Code Style

- Google Java Style, enforced by Spotless in the Gradle build.
- Line length: whatever `google-java-format` decides (default 100 columns). We accept the tool's opinion rather than fight it.
- No wildcard imports.
- No `Optional` fields on records or entities; use nullable-annotated fields or wrap at read time.
- No `null` returns from methods that could return `Optional<T>`. Use `Optional` at API surfaces, avoid it in performance-critical inner loops.
- Prefer `record`, `sealed interface`, and pattern-matching `switch` for closed hierarchies.
- Package-private is the default visibility; escalate to `public` only when crossing package boundaries.
