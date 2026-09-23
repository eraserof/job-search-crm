package com.jobcrm.core.domain.company;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for a {@code Company} aggregate root. */
public record CompanyId(UUID value) {
  public CompanyId {
    Objects.requireNonNull(value, "value");
  }

  public static CompanyId newId() {
    return new CompanyId(UUID.randomUUID());
  }
}
