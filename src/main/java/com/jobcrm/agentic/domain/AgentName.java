package com.jobcrm.agentic.domain;

/**
 * Enumeration of every concrete agent in the system. Used to key per-agent config, per-agent
 * mutexes in the dispatcher, and the {@code agent} column of {@code agent_run}.
 */
public enum AgentName {
  EMAIL_INGEST,
  CALL_NOTE_INGEST,
  LINKEDIN_INGEST,
  DRAFT_REPLY,
  INTERVIEW_PREP,
  QUERY,
  STALE_DETECT
}
