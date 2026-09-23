package com.jobcrm.core.domain;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for a {@code Contact} aggregate root. */
public record ContactId(UUID value) {
  public ContactId {
    Objects.requireNonNull(value, "value");
  }

  public static ContactId newId() {
    return new ContactId(UUID.randomUUID());
  }
}
