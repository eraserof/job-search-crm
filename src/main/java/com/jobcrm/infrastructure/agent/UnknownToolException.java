package com.jobcrm.infrastructure.agent;

public class UnknownToolException extends RuntimeException {
  UnknownToolException(String message) {
    super(message);
  }
}
