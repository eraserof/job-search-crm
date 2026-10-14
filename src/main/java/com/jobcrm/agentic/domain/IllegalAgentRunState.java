package com.jobcrm.agentic.domain;

public final class IllegalAgentRunState extends RuntimeException {

  private final AgentRunStatus currentState;
  private final AgentRunStatus toState;

  public IllegalAgentRunState(AgentRunStatus currentState, AgentRunStatus toState) {
    super("Cannot transition from " + currentState + " to " + toState);
    this.currentState = currentState;
    this.toState = toState;
  }

  public IllegalAgentRunState(AgentRunStatus currentState) {
    super("Cannot append agent turn while run is in state " + currentState);
    this.currentState = currentState;
    this.toState = AgentRunStatus.RUNNING;
  }

  public AgentRunStatus current() {
    return currentState;
  }

  public AgentRunStatus to() {
    return toState;
  }
}
