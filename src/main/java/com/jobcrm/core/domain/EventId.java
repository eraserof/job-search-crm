package com.jobcrm.core.domain;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for an {@code Event} entity (child of {@code Opportunity}). */
public record EventId(UUID value) {
  public EventId {
    Objects.requireNonNull(value, "value");
  }

  public static EventId newId() {
    return new EventId(UUID.randomUUID());
  }
}
