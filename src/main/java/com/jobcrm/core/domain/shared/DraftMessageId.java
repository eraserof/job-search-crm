package com.jobcrm.core.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a {@code DraftMessage}. The aggregate itself lives in the {@code
 * messaging} context; the identifier lives here in {@code core.shared} as shared vocabulary so that
 * a {@code Task} (in core) can reference a draft without inverting the bounded-context dependency
 * direction (messaging → core).
 */
public record DraftMessageId(UUID value) {
  public DraftMessageId {
    Objects.requireNonNull(value, "value");
  }

  public static DraftMessageId newId() {
    return new DraftMessageId(UUID.randomUUID());
  }
}
