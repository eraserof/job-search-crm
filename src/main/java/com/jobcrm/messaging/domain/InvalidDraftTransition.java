package com.jobcrm.messaging.domain;

/**
 * Thrown when an operation on a {@code DraftMessage} would violate its status state machine (e.g.,
 * approving a draft that is already discarded, or marking-sent a draft that was never approved).
 */
public final class InvalidDraftTransition extends RuntimeException {

  private final DraftStatus from;
  private final String operation;

  public InvalidDraftTransition(DraftStatus from, String operation) {
    super("Cannot " + operation + " a draft in status " + from);
    this.from = from;
    this.operation = operation;
  }

  public DraftStatus from() {
    return from;
  }

  public String operation() {
    return operation;
  }
}
