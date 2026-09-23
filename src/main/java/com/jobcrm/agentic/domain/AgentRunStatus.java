package com.jobcrm.agentic.domain;

/**
 * Terminal-oriented status of an {@code AgentRun}. A run starts as {@code RUNNING} and finishes in
 * exactly one of the three terminal states.
 */
public enum AgentRunStatus {
  RUNNING,
  SUCCESS,
  FAILED,
  ABORTED
}
