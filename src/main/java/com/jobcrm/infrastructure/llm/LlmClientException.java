package com.jobcrm.infrastructure.llm;

public class LlmClientException extends RuntimeException {
  public LlmClientException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmClientException(String message) {
    super(message);
  }
}
