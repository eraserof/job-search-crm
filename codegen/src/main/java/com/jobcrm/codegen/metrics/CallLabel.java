package com.jobcrm.codegen.metrics;

/**
 * The name of the piece of work the tool is doing right now, e.g. {@code ContractCheck.literals}
 * for a job or {@code plan-contractcheck} for a request. Metrics read it to fill {@link
 * CallRecord#job()}. One instance is shared by the whole run, which is sequential, so one slot is
 * enough.
 */
public final class CallLabel {

  private volatile String current;

  /** The label in effect, or null when none is set. */
  public String current() {
    return current;
  }

  /**
   * Sets {@code label} until the returned scope is closed, then restores the previous label. Use in
   * try-with-resources so an exception cannot leave a stale label behind.
   */
  public Scope use(String label) {
    String previous = current;
    current = label;
    return () -> current = previous;
  }

  /** Closing never throws, so it needs no catch block. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
