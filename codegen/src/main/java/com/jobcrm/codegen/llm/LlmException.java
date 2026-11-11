package com.jobcrm.codegen.llm;

/**
 * A model call failed. {@link #retryable()} is true for conditions that may clear on their own
 * (server busy, unreachable, timed out, stalled) and false for ones that will not (bad request,
 * unknown model, server-reported error).
 */
public final class LlmException extends Exception {

  private final boolean retryable;

  private LlmException(String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.retryable = retryable;
  }

  public static LlmException retryable(String message) {
    return new LlmException(message, true, null);
  }

  public static LlmException retryable(String message, Throwable cause) {
    return new LlmException(message, true, cause);
  }

  public static LlmException fatal(String message) {
    return new LlmException(message, false, null);
  }

  public static LlmException fatal(String message, Throwable cause) {
    return new LlmException(message, false, cause);
  }

  public boolean retryable() {
    return retryable;
  }
}
