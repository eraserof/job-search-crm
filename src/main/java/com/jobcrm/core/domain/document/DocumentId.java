package com.jobcrm.core.domain.document;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for a {@code Document} entity. */
public record DocumentId(UUID value) {
  public DocumentId {
    Objects.requireNonNull(value, "value");
  }

  public static DocumentId newId() {
    return new DocumentId(UUID.randomUUID());
  }
}
