-- V1__init.sql — initial core schema for the Job-Search CRM.
--
-- Transcribed from .kiro/specs/job-search-crm/domain-model.md § SQLite Schema.
-- Conventions:
--   * UUIDs stored as TEXT.
--   * Timestamps stored as ISO-8601 TEXT.
--   * Foreign keys rely on `PRAGMA foreign_keys=ON`, set per-connection in
--     DataSourceConfig#buildSource (Hikari connectionInitSql).
--
-- The agentic context's tables (agent_run, agent_turn) are intentionally NOT
-- here; they arrive with the agent runtime (see agent-architecture.md).

CREATE TABLE company (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    domain        TEXT,
    created_at    TEXT NOT NULL,
    UNIQUE (name)
);
CREATE INDEX idx_company_domain ON company (domain);

CREATE TABLE contact (
    id            TEXT PRIMARY KEY,
    display_name  TEXT NOT NULL,
    phone         TEXT,
    linkedin_url  TEXT,
    employer_id   TEXT REFERENCES company (id),
    default_roles TEXT NOT NULL,        -- JSON array
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE TABLE contact_email (
    contact_id    TEXT NOT NULL REFERENCES contact (id) ON DELETE CASCADE,
    email         TEXT NOT NULL,        -- canonical lowercase
    PRIMARY KEY (contact_id, email),
    UNIQUE (email)                      -- one email -> one contact
);
CREATE INDEX idx_contact_email_addr ON contact_email (email);

CREATE TABLE opportunity (
    id                 TEXT PRIMARY KEY,
    company_id         TEXT NOT NULL REFERENCES company (id),
    role               TEXT NOT NULL,
    stage              TEXT NOT NULL,
    resume_document_id TEXT,
    created_at         TEXT NOT NULL,
    updated_at         TEXT NOT NULL
);
CREATE INDEX idx_opportunity_stage   ON opportunity (stage);
CREATE INDEX idx_opportunity_company ON opportunity (company_id);

CREATE TABLE opportunity_contact (
    opportunity_id TEXT NOT NULL REFERENCES opportunity (id) ON DELETE CASCADE,
    contact_id     TEXT NOT NULL REFERENCES contact (id),
    role_in_opp    TEXT NOT NULL,
    PRIMARY KEY (opportunity_id, contact_id)
);

CREATE TABLE interaction (
    id             TEXT PRIMARY KEY,
    opportunity_id TEXT NOT NULL REFERENCES opportunity (id) ON DELETE CASCADE,
    channel        TEXT NOT NULL,
    direction      TEXT NOT NULL,
    occurred_at    TEXT NOT NULL,
    thread_key     TEXT,
    external_id    TEXT,
    subject        TEXT,
    body_text      TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    UNIQUE (external_id)
);
CREATE INDEX idx_interaction_opp      ON interaction (opportunity_id);
CREATE INDEX idx_interaction_thread   ON interaction (thread_key);
CREATE INDEX idx_interaction_occurred ON interaction (occurred_at);

CREATE TABLE interaction_contact (
    interaction_id TEXT NOT NULL REFERENCES interaction (id) ON DELETE CASCADE,
    contact_id     TEXT NOT NULL REFERENCES contact (id),
    PRIMARY KEY (interaction_id, contact_id)
);

CREATE TABLE event (
    id                TEXT PRIMARY KEY,
    opportunity_id    TEXT NOT NULL REFERENCES opportunity (id) ON DELETE CASCADE,
    kind              TEXT NOT NULL,
    starts_at         TEXT NOT NULL,
    ends_at           TEXT NOT NULL,
    calendar_event_id TEXT,
    title             TEXT NOT NULL,
    notes             TEXT,
    created_at        TEXT NOT NULL,
    UNIQUE (calendar_event_id)
);
CREATE INDEX idx_event_opp  ON event (opportunity_id);
CREATE INDEX idx_event_ends ON event (ends_at);

CREATE TABLE event_participant (
    event_id   TEXT NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    contact_id TEXT NOT NULL REFERENCES contact (id),
    PRIMARY KEY (event_id, contact_id)
);

CREATE TABLE task (
    id                  TEXT PRIMARY KEY,
    opportunity_id      TEXT NOT NULL REFERENCES opportunity (id) ON DELETE CASCADE,
    type                TEXT NOT NULL,
    due_at              TEXT NOT NULL,
    status              TEXT NOT NULL,
    associated_draft_id TEXT,
    note                TEXT,
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL
);
CREATE INDEX idx_task_status_due ON task (status, due_at);
CREATE INDEX idx_task_opp        ON task (opportunity_id);

CREATE TABLE draft_message (
    id             TEXT PRIMARY KEY,
    opportunity_id TEXT NOT NULL REFERENCES opportunity (id) ON DELETE CASCADE,
    recipient_id   TEXT NOT NULL REFERENCES contact (id),
    channel        TEXT NOT NULL,
    subject        TEXT NOT NULL,
    body           TEXT NOT NULL,
    status         TEXT NOT NULL,
    reviewer_note  TEXT,
    created_at     TEXT NOT NULL,
    reviewed_at    TEXT
);
CREATE INDEX idx_draft_status ON draft_message (status);

CREATE TABLE document (
    id             TEXT PRIMARY KEY,
    opportunity_id TEXT REFERENCES opportunity (id) ON DELETE SET NULL,
    kind           TEXT NOT NULL,
    filename       TEXT NOT NULL,
    stored_path    TEXT NOT NULL,
    created_at     TEXT NOT NULL
);

-- Single-row cursors for the ingest context.
CREATE TABLE gmail_cursor (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    history_id     TEXT,
    last_polled_at TEXT NOT NULL
);

CREATE TABLE calendar_cursor (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    sync_token     TEXT,
    last_polled_at TEXT NOT NULL
);
