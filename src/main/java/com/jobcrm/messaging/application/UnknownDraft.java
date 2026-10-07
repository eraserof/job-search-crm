package com.jobcrm.messaging.application;

/**
 * Thrown when a draft-review operation targets a draft id that does not exist. Unchecked, so it
 * propagates to the CLI's top-level handler without forcing {@code throws} on intermediate layers.
 */
public class UnknownDraft extends RuntimeException {

  private final String id;

  public UnknownDraft(String id) {
    super("Unknown draft: " + id);
    this.id = id;
  }

  public String id() {
    return id;
  }
}
