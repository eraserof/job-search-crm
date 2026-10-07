package com.jobcrm.core.application;

/**
 * Thrown by an application service when an operation targets an aggregate id that does not exist.
 * Unchecked so it propagates to the CLI's top-level handler without forcing {@code throws} on every
 * layer, consistent with the error-handling strategy used for persistence failures.
 */
public class UnknownAggregate extends RuntimeException {

  private final String type;
  private final String id;

  public UnknownAggregate(String type, String id) {
    super("Unknown " + type + ": " + id);
    this.type = type;
    this.id = id;
  }

  public String type() {
    return type;
  }

  public String id() {
    return id;
  }
}
