package com.jobcrm.agentic.domain;

public sealed interface AgentRunOutcome {
  AgentRunId runId();

  record Success(AgentRunId runId, String finalText) implements AgentRunOutcome {}

  record Failed(AgentRunId runId, String errorMessage) implements AgentRunOutcome {}

  record Aborted(AgentRunId runId, String reason) implements AgentRunOutcome {}
}
