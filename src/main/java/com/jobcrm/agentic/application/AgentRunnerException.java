package com.jobcrm.agentic.application;

public class AgentRunnerException extends RuntimeException {
  public AgentRunnerException(String message) {
    super(message);
  }

  public AgentRunnerException(String message, Throwable e) {
    super(message, e);
  }
}
