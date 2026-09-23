package com.jobcrm.core.domain.interaction;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for an {@code Interaction} entity (child of {@code Opportunity}). */
public record InteractionId(UUID value) {
  public InteractionId {
    Objects.requireNonNull(value, "value");
  }

  public static InteractionId newId() {
    return new InteractionId(UUID.randomUUID());
  }
}
