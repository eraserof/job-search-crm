# Domain Model

Aggregates, entities, value objects, repositories, schema, and invariants for the Job-Search CRM. This document is scoped to the domain layer as defined in `.kiro/steering/development-guidelines.md`. For agent-runtime concepts (AgentRun, Tools, LlmClient) see `agent-architecture.md`. For CLI surfaces see `ui-cli.md`. For the big-picture map see `design.md`.

---

## Aggregate Overview

| Aggregate root | Bounded context | Purpose |
|----------------|-----------------|---------|
| `Opportunity` | core | The central anchor: one role at one company through its full lifecycle. Owns interactions, events, tasks, documents. |
| `Contact` | core | A person that may appear across multiple opportunities. |
| `Company` | core | Grouping entity; opportunities and contacts hang off it. |
| `DraftMessage` | messaging | A draft reply awaiting user review. |
| `AgentRun` | agentic (see `agent-architecture.md`) | Immutable audit record of one agent invocation. |

---

## Opportunity (aggregate root)

The central entity. Every job-search interaction eventually attaches to an Opportunity.

```java
public final class Opportunity {
    public OpportunityId id();
    public CompanyId companyId();
    public String role();
    public Stage stage();
    public Instant createdAt();
    public Instant updatedAt();
    public Optional<DocumentId> resumeVersionSent();
    public List<ContactRef> contacts();          // read-only view
    public List<InteractionId> interactions();   // read-only view
    public List<EventId> events();
    public List<TaskId> openTasks();

    // Domain operations
    public void advanceStage(Stage next);
    public void attachContact(ContactId c, ContactRole r);
    public void detachContact(ContactId c);
    public void recordInteraction(Interaction i);
    public void scheduleEvent(Event e);
    public void createTask(Task t);
    public void markGhosted(Clock clock, Duration silentFor);
}
```

Responsibilities:

- Enforce stage-transition rules (rejected can't advance to onsite).
- Guard that any recorded interaction references at least one attached contact.
- Emit domain events on stage change and interaction record (used by the rules engine).

---

## Contact (aggregate root)

```java
public final class Contact {
    public ContactId id();
    public String displayName();
    public List<EmailAddress> emails();
    public Optional<String> phone();
    public Optional<String> linkedInUrl();
    public Optional<CompanyId> employer();
    public Set<ContactRole> defaultRoles();

    public void addEmail(EmailAddress e);
    public void setEmployer(CompanyId c);
    public void rename(String newName);
}
```

Responsibilities:

- Deduplication by canonical email address.
- Identity resolution for inbound email: given a `From:` address, return the contact.

---

## Company (aggregate root)

```java
public final class Company {
    public CompanyId id();
    public String name();
    public Optional<String> domain();   // e.g., "stripe.com" — used for email routing

    public void renameTo(String newName);
    public void setDomain(String domain);
}
```

---

## Entities (children of the Opportunity aggregate)

```java
public final class Interaction {
    public InteractionId id();
    public OpportunityId opportunity();
    public List<ContactId> contacts();
    public Channel channel();             // EMAIL, LINKEDIN, PHONE_CALL_NOTE
    public Direction direction();         // INBOUND, OUTBOUND
    public Instant occurredAt();
    public Optional<String> threadKey();  // Gmail thread-id, LI thread key
    public String bodyText();             // plain text; HTML stripped upstream
    public Optional<String> subject();
    public Optional<String> externalId(); // Gmail messageId, etc.
}

public final class Event {
    public EventId id();
    public OpportunityId opportunity();
    public EventKind kind();              // INTERVIEW, RECRUITER_CALL, ONSITE, OTHER
    public Instant startsAt();
    public Instant endsAt();
    public List<ContactId> participants();
    public Optional<String> calendarEventId();
    public String title();
    public Optional<String> notes();
}

public final class Task {
    public TaskId id();
    public OpportunityId opportunity();
    public TaskType type();               // THANK_YOU, NUDGE, RESPOND, PREP, OTHER
    public Instant dueAt();
    public TaskStatus status();           // OPEN, DONE, DISMISSED
    public Optional<DraftMessageId> associatedDraft();
    public Optional<String> note();
}

public final class Document {
    public DocumentId id();
    public Optional<OpportunityId> opportunity();
    public DocumentKind kind();           // RESUME, COVER_LETTER, JD, OTHER
    public String filename();
    public String storedPath();           // path under app data dir
    public Instant createdAt();
}
```

---

## DraftMessage (aggregate root, messaging context)

```java
public final class DraftMessage {
    public DraftMessageId id();
    public OpportunityId opportunity();
    public ContactId recipient();
    public Channel channel();
    public String subject();
    public String body();
    public DraftStatus status();
    public Instant createdAt();
    public Optional<Instant> reviewedAt();
    public Optional<String> reviewerNote();

    public void approve(String note);
    public void discard(String note);
    public void markSent();  // v1: user manually confirms after copying to Gmail
}
```

State machine: `PENDING_REVIEW → APPROVED → SENT` or `PENDING_REVIEW → DISCARDED`.

---

## Value Objects

```java
public record OpportunityId(UUID value) {}
public record ContactId(UUID value) {}
public record CompanyId(UUID value) {}
public record InteractionId(UUID value) {}
public record EventId(UUID value) {}
public record TaskId(UUID value) {}
public record DraftMessageId(UUID value) {}
public record DocumentId(UUID value) {}

public record EmailAddress(String canonical) {
    public EmailAddress {
        canonical = canonical.trim().toLowerCase(Locale.ROOT);
        // validate RFC-5321-ish: exactly one '@', non-empty local + domain
    }
    public String domain() { return canonical.substring(canonical.indexOf('@') + 1); }
}

public record ContactRef(ContactId contactId, ContactRole roleInThisOpp) {}

public enum Stage {
    APPLIED, RECRUITER_SCREEN, HM_SCREEN, PANEL, ONSITE,
    OFFER, REJECTED, GHOSTED, WITHDRAWN;

    public boolean isTerminal();
    public boolean canTransitionTo(Stage next);
}

public enum Channel { EMAIL, LINKEDIN, PHONE_CALL_NOTE }
public enum Direction { INBOUND, OUTBOUND }
public enum ContactRole {
    RECRUITER_INTERNAL, RECRUITER_EXTERNAL,
    HIRING_MANAGER, PANELIST, REFERRAL, OTHER
}
public enum TaskType { THANK_YOU, NUDGE, RESPOND, PREP, OTHER }
public enum TaskStatus { OPEN, DONE, DISMISSED }
public enum EventKind { INTERVIEW, RECRUITER_CALL, ONSITE, OTHER }
public enum DraftStatus { PENDING_REVIEW, APPROVED, DISCARDED, SENT }
public enum DocumentKind { RESUME, COVER_LETTER, JD, OTHER }
```

---

## Stage Transition Matrix

```pascal
FUNCTION Stage.canTransitionTo(next)
  match self with
    | APPLIED          → next IN {RECRUITER_SCREEN, HM_SCREEN, REJECTED, GHOSTED, WITHDRAWN}
    | RECRUITER_SCREEN → next IN {HM_SCREEN, PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN}
    | HM_SCREEN        → next IN {PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN}
    | PANEL            → next IN {ONSITE, OFFER, REJECTED, GHOSTED, WITHDRAWN}
    | ONSITE           → next IN {OFFER, REJECTED, GHOSTED, WITHDRAWN}
    | OFFER            → next IN {REJECTED, WITHDRAWN}          // acceptance is out-of-domain
    | REJECTED         → false                                   // terminal
    | GHOSTED          → next IN {RECRUITER_SCREEN, HM_SCREEN}   // re-engagement path
    | WITHDRAWN        → false                                   // terminal
END
```

`isTerminal()` returns `true` for `REJECTED` and `WITHDRAWN`.

---

## Repositories

Interfaces live in `domain`; implementations live in `infrastructure.persistence` (JDBC over SQLite).

```java
public interface OpportunityRepository {
    Optional<Opportunity> findById(OpportunityId id);
    List<Opportunity> findByCompany(CompanyId company);
    List<Opportunity> findByStage(Stage stage);
    List<Opportunity> searchByText(String query, int limit);
    List<Opportunity> findSilentSince(Instant threshold);   // no interaction after threshold
    void save(Opportunity o);
}

public interface ContactRepository {
    Optional<Contact> findById(ContactId id);
    Optional<Contact> findByEmail(EmailAddress email);
    List<Contact> searchByName(String query, int limit);
    List<Contact> findByCompany(CompanyId company);
    void save(Contact c);
}

public interface CompanyRepository {
    Optional<Company> findById(CompanyId id);
    Optional<Company> findByName(String name);
    Optional<Company> findByDomain(String domain);
    void save(Company c);
}

public interface InteractionRepository {
    Optional<Interaction> findById(InteractionId id);
    List<Interaction> findByOpportunity(OpportunityId opp);
    Optional<Interaction> findByExternalId(String externalId);  // idempotency
    List<Interaction> findByThreadKey(String threadKey);
    void save(Interaction i);
}

public interface EventRepository {
    Optional<Event> findById(EventId id);
    List<Event> findByOpportunity(OpportunityId opp);
    List<Event> findEndedBetween(Instant from, Instant to);
    Optional<Event> findByCalendarEventId(String calId);
    void save(Event e);
}

public interface TaskRepository {
    Optional<Task> findById(TaskId id);
    List<Task> findOpenByOpportunity(OpportunityId opp);
    List<Task> findDueBy(Instant threshold);
    void save(Task t);
}

public interface DraftMessageRepository {
    Optional<DraftMessage> findById(DraftMessageId id);
    List<DraftMessage> findPendingReview();
    List<DraftMessage> findByOpportunity(OpportunityId opp);
    void save(DraftMessage d);
}
```

---

## Validation Rules

- `EmailAddress.canonical`: RFC-5321-ish check, lowercased, trimmed. Enforced in the compact constructor.
- `Company.name`: non-empty, unique across the table (case-insensitive at read time).
- `Opportunity.role`: non-empty.
- `Stage`: transitions constrained by `Stage.canTransitionTo`.
- `Interaction.bodyText`: non-null, may be empty (e.g., reactions).
- `Interaction.externalId`: unique when present — enforces idempotent ingestion.
- `Task.dueAt`: must be ≥ `Task.createdAt`.
- `DraftMessage.status`: obeys the state machine above.

---

## SQLite Schema

Flyway-managed under `infrastructure/migrations/V{n}__*.sql`. UUIDs are stored as TEXT for readability.

```sql
-- V1__init.sql
CREATE TABLE company (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    domain        TEXT,
    created_at    TEXT NOT NULL,
    UNIQUE(name)
);
CREATE INDEX idx_company_domain ON company(domain);

CREATE TABLE contact (
    id            TEXT PRIMARY KEY,
    display_name  TEXT NOT NULL,
    phone         TEXT,
    linkedin_url  TEXT,
    employer_id   TEXT REFERENCES company(id),
    default_roles TEXT NOT NULL,        -- JSON array
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE TABLE contact_email (
    contact_id    TEXT NOT NULL REFERENCES contact(id) ON DELETE CASCADE,
    email         TEXT NOT NULL,        -- canonical lowercase
    PRIMARY KEY (contact_id, email),
    UNIQUE(email)                       -- one email → one contact
);
CREATE INDEX idx_contact_email_addr ON contact_email(email);

CREATE TABLE opportunity (
    id                    TEXT PRIMARY KEY,
    company_id            TEXT NOT NULL REFERENCES company(id),
    role                  TEXT NOT NULL,
    stage                 TEXT NOT NULL,
    resume_document_id    TEXT,
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL
);
CREATE INDEX idx_opportunity_stage   ON opportunity(stage);
CREATE INDEX idx_opportunity_company ON opportunity(company_id);

CREATE TABLE opportunity_contact (
    opportunity_id  TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
    contact_id      TEXT NOT NULL REFERENCES contact(id),
    role_in_opp     TEXT NOT NULL,
    PRIMARY KEY (opportunity_id, contact_id)
);

CREATE TABLE interaction (
    id              TEXT PRIMARY KEY,
    opportunity_id  TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
    channel         TEXT NOT NULL,
    direction       TEXT NOT NULL,
    occurred_at     TEXT NOT NULL,
    thread_key      TEXT,
    external_id     TEXT,
    subject         TEXT,
    body_text       TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    UNIQUE(external_id)
);
CREATE INDEX idx_interaction_opp      ON interaction(opportunity_id);
CREATE INDEX idx_interaction_thread   ON interaction(thread_key);
CREATE INDEX idx_interaction_occurred ON interaction(occurred_at);

CREATE TABLE interaction_contact (
    interaction_id TEXT NOT NULL REFERENCES interaction(id) ON DELETE CASCADE,
    contact_id     TEXT NOT NULL REFERENCES contact(id),
    PRIMARY KEY (interaction_id, contact_id)
);

CREATE TABLE event (
    id                  TEXT PRIMARY KEY,
    opportunity_id      TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
    kind                TEXT NOT NULL,
    starts_at           TEXT NOT NULL,
    ends_at             TEXT NOT NULL,
    calendar_event_id   TEXT,
    title               TEXT NOT NULL,
    notes               TEXT,
    created_at          TEXT NOT NULL,
    UNIQUE(calendar_event_id)
);
CREATE INDEX idx_event_opp  ON event(opportunity_id);
CREATE INDEX idx_event_ends ON event(ends_at);

CREATE TABLE event_participant (
    event_id   TEXT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    contact_id TEXT NOT NULL REFERENCES contact(id),
    PRIMARY KEY (event_id, contact_id)
);

CREATE TABLE task (
    id                     TEXT PRIMARY KEY,
    opportunity_id         TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
    type                   TEXT NOT NULL,
    due_at                 TEXT NOT NULL,
    status                 TEXT NOT NULL,
    associated_draft_id    TEXT,
    note                   TEXT,
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL
);
CREATE INDEX idx_task_status_due ON task(status, due_at);
CREATE INDEX idx_task_opp        ON task(opportunity_id);

CREATE TABLE draft_message (
    id                TEXT PRIMARY KEY,
    opportunity_id    TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
    recipient_id      TEXT NOT NULL REFERENCES contact(id),
    channel           TEXT NOT NULL,
    subject           TEXT NOT NULL,
    body              TEXT NOT NULL,
    status            TEXT NOT NULL,
    reviewer_note     TEXT,
    created_at        TEXT NOT NULL,
    reviewed_at       TEXT
);
CREATE INDEX idx_draft_status ON draft_message(status);

CREATE TABLE document (
    id             TEXT PRIMARY KEY,
    opportunity_id TEXT REFERENCES opportunity(id) ON DELETE SET NULL,
    kind           TEXT NOT NULL,
    filename       TEXT NOT NULL,
    stored_path    TEXT NOT NULL,
    created_at     TEXT NOT NULL
);

-- Cursors for the ingest context (see agent-architecture.md and design.md)
CREATE TABLE gmail_cursor (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    history_id      TEXT,
    last_polled_at  TEXT NOT NULL
);

CREATE TABLE calendar_cursor (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    sync_token      TEXT,
    last_polled_at  TEXT NOT NULL
);
```

The `agent_run` and `agent_turn` tables live in `agent-architecture.md` because they belong to the agentic context.

---

## Migration Strategy

- Flyway with SQL migrations under `infrastructure/migrations/V{n}__desc.sql`.
- Startup runs migrations before Spring wires repositories.
- Additive-only within a released major (add columns nullable, add tables, add indexes).
- Data-shape changes go through a `V{n}__migrate_*.sql` that transforms in SQL, followed a release later by `V{n+1}__drop_old.sql` — keeps rollbacks tractable.

---

## Correctness Properties (Domain)

These properties seed the jqwik property-based test suite.

1. **Stage-transition safety.** For all opportunities `o` and stages `s`, `advanceStage(o.id, s)` succeeds if and only if `o.stage.canTransitionTo(s)`. On failure, `o.stage` is unchanged.
2. **Draft state machine.** For all `DraftMessage d`, `d.status` transitions follow `PENDING_REVIEW → APPROVED → SENT` or `PENDING_REVIEW → DISCARDED`. No other transitions are permitted.
3. **Contact email uniqueness.** For all `EmailAddress` values `x`, there is at most one `Contact` `c` such that `x ∈ c.emails()`.
4. **Interaction integrity.** For every `Interaction i`, `i.opportunityId` refers to an existing opportunity and each `contactId ∈ i.contacts` refers to an existing contact attached to that opportunity.
5. **Idempotent externalId.** For all interactions with a non-empty `externalId`, exactly one row exists per unique value across all inserts.
6. **Task–draft coupling.** For every `Task t` with `t.associatedDraft` present, the draft's opportunity equals `t.opportunity`.

Ingest-idempotency and agent-side properties live in `agent-architecture.md`.

---

## Key Application-Service Signatures

Application services are transactional facades. Signatures below; behavior detailed in `design.md`.

```java
public interface OpportunityService {
    OpportunityId create(CreateOpportunityCommand cmd);
    void advanceStage(OpportunityId id, Stage next);           // throws IllegalStageTransition
    void attachContact(OpportunityId id, ContactId c, ContactRole r);
    void recordInteraction(OpportunityId id, RecordInteractionCommand cmd);
}

public interface ContactService {
    ContactId createOrMerge(CreateContactCommand cmd);         // dedup by canonical email
    void rename(ContactId id, String newName);
    void addEmail(ContactId id, EmailAddress e);
}

public interface DraftReviewService {
    List<DraftMessage> listPending();
    void approve(DraftMessageId id, String note);
    void discard(DraftMessageId id, String note);
    void markSent(DraftMessageId id);
}
```
