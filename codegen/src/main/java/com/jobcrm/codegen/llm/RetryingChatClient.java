package com.jobcrm.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcrm.codegen.config.ResolvedStage;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Optional;

/**
 * Decorator that retries {@link LlmException#retryable() retryable} failures with backoff from a
 * {@link RetryPolicy}. Fatal failures are rethrown immediately, without waiting.
 */
public final class RetryingChatClient implements ChatClient {

  private final ChatClient delegate;
  private final RetryPolicy policy;
  private final Sleeper sleeper;
  private final PrintWriter out;

  public RetryingChatClient(
      ChatClient delegate, RetryPolicy policy, Sleeper sleeper, PrintWriter out) {
    this.delegate = delegate;
    this.policy = policy;
    this.sleeper = sleeper;
    this.out = out;
  }

  /**
   * Calls {@code delegate.complete(stage, prompt, responseFormat)} and returns its result.
   *
   * <ol>
   *   <li>Keep a count of failures, starting at 0.
   *   <li>Loop: call the delegate; on success return the text.
   *   <li>On LlmException e: if {@code !e.retryable()}, rethrow e. Otherwise increment the count
   *       and ask {@code policy.delayAfter(count)}. If empty, rethrow e.
   *   <li>Otherwise print {@code "model call failed (" + e.getMessage() + "); retrying in " +
   *       delay.toSeconds() + "s"} with {@code out.println} and {@code out.flush()}, then {@code
   *       sleeper.sleep(delay)} and loop.
   *   <li>If the sleep throws InterruptedException, restore the interrupt flag with {@code
   *       Thread.currentThread().interrupt()} and throw {@code LlmException.fatal("interrupted
   *       while waiting to retry", e)} using the original LlmException e as the cause.
   * </ol>
   */
  @Override
  public String complete(ResolvedStage stage, String prompt, JsonNode responseFormat)
      throws LlmException {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
