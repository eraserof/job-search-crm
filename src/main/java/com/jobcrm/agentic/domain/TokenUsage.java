package com.jobcrm.agentic.domain;

/**
 * LLM token accounting for a single call or aggregated across a run. Used for cost bookkeeping in
 * {@code AgentRun}.
 */
public record TokenUsage(long inputTokens, long outputTokens) {

  /** Neutral element for {@link #add(TokenUsage)}. */
  public static final TokenUsage ZERO = new TokenUsage(0L, 0L);

  public TokenUsage {
    if (inputTokens < 0L) {
      throw new IllegalArgumentException("inputTokens must be >= 0, got " + inputTokens);
    }
    if (outputTokens < 0L) {
      throw new IllegalArgumentException("outputTokens must be >= 0, got " + outputTokens);
    }
  }

  /** Component-wise sum. */
  public TokenUsage add(TokenUsage other) {
    return new TokenUsage(
        this.inputTokens + other.inputTokens, this.outputTokens + other.outputTokens);
  }

  public long total() {
    return inputTokens + outputTokens;
  }
}
