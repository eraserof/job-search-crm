package com.jobcrm.infrastructure.llm;

/**
 * Thrown when (de)serializing JSON to/from the LLM provider fails. Unchecked so the failure
 * propagates to the CLI's top-level handler without forcing {@code throws} on every layer, matching
 * the project's error-handling convention.
 */
public class LlmJsonException extends RuntimeException {

  public LlmJsonException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmJsonException(String message) {
    super(message);
  }
}
