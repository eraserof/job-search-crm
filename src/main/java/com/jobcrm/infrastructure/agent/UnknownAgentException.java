package com.jobcrm.infrastructure.agent;

public class UnknownAgentException extends RuntimeException {
  public UnknownAgentException(String message) {
    super(message);
  }
}
