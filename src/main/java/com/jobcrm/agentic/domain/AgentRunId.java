package com.jobcrm.agentic.domain;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for an {@code AgentRun} aggregate root. */
public record AgentRunId(UUID value) {
  public AgentRunId {
    Objects.requireNonNull(value, "value");
  }

  public static AgentRunId newId() {
    return new AgentRunId(UUID.randomUUID());
  }
}
