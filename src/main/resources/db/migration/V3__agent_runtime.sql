-- V3__agent_runtime.sql — agentic context: AgentRun audit trail.
--
-- Transcribed from .kiro/specs/job-search-crm/agent-architecture.md § AgentRun schema.
-- Conventions (same as V1):
--   * UUIDs stored as TEXT.
--   * Timestamps stored as ISO-8601 TEXT.
--   * Foreign keys rely on `PRAGMA foreign_keys=ON`, set per-connection in
--     DataSourceConfig (Hikari data-source properties).
--
-- An AgentRun is an immutable audit record of one agent invocation; agent_turn
-- is its append-only, ordered turn log. Turns cascade-delete with their run so
-- the retention/pruning job can drop a run in one statement.

CREATE TABLE agent_run (
    id             TEXT PRIMARY KEY,
    agent          TEXT NOT NULL,
    status         TEXT NOT NULL,          -- RUNNING, SUCCESS, FAILED, ABORTED
    started_at     TEXT NOT NULL,
    finished_at    TEXT,
    input_json     TEXT NOT NULL,
    final_output   TEXT,
    input_tokens   INTEGER NOT NULL DEFAULT 0,
    output_tokens  INTEGER NOT NULL DEFAULT 0,
    error_message  TEXT
);
CREATE INDEX idx_agent_run_agent_started ON agent_run (agent, started_at);

CREATE TABLE agent_turn (
    id               TEXT PRIMARY KEY,
    agent_run_id     TEXT NOT NULL REFERENCES agent_run (id) ON DELETE CASCADE,
    turn_index       INTEGER NOT NULL,
    role             TEXT NOT NULL,        -- SYSTEM, USER, ASSISTANT, TOOL
    content_json     TEXT NOT NULL,        -- System/User text, Assistant contentJson, or tool-call payload for TOOL turns
    tool_name        TEXT,
    tool_args_json   TEXT,
    tool_result_json TEXT,
    created_at       TEXT NOT NULL,
    UNIQUE (agent_run_id, turn_index)
);
