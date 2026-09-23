package com.jobcrm.messaging.domain;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for a {@code DraftMessage} aggregate root. */
public record DraftMessageId(UUID value) {
  public DraftMessageId {
    Objects.requireNonNull(value, "value");
  }

  public static DraftMessageId newId() {
    return new DraftMessageId(UUID.randomUUID());
  }
}
