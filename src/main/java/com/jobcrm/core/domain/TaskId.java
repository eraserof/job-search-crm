package com.jobcrm.core.domain;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for a {@code Task} entity (child of {@code Opportunity}). */
public record TaskId(UUID value) {
  public TaskId {
    Objects.requireNonNull(value, "value");
  }

  public static TaskId newId() {
    return new TaskId(UUID.randomUUID());
  }
}
