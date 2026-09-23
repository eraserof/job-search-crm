package com.jobcrm.core.domain.opportunity;

/**
 * Thrown when a caller attempts an illegal {@link Stage} transition on an {@code Opportunity}. The
 * allowed transitions are enforced by {@link Stage#canTransitionTo(Stage)}.
 */
public final class IllegalStageTransition extends RuntimeException {

  private final Stage from;
  private final Stage to;

  public IllegalStageTransition(Stage from, Stage to) {
    super("Illegal stage transition: " + from + " -> " + to);
    this.from = from;
    this.to = to;
  }

  public Stage from() {
    return from;
  }

  public Stage to() {
    return to;
  }
}
