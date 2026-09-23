package com.jobcrm.core.domain.opportunity;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for an {@code Opportunity} aggregate root. */
public record OpportunityId(UUID value) {
  public OpportunityId {
    Objects.requireNonNull(value, "value");
  }

  public static OpportunityId newId() {
    return new OpportunityId(UUID.randomUUID());
  }
}
