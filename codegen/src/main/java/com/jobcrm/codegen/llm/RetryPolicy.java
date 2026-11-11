package com.jobcrm.codegen.llm;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * How long to wait before each retry of a retryable model failure. {@code delays.get(0)} is the
 * wait after the first failure, {@code delays.get(1)} after the second, and so on; once the list is
 * used up, there are no more retries.
 */
public record RetryPolicy(List<Duration> delays) {

  public RetryPolicy {
    delays = List.copyOf(delays);
  }

  /** 30 s, 60 s, 120 s: three retries, about 3.5 minutes of waiting in total. */
  public static RetryPolicy standard() {
    return new RetryPolicy(
        List.of(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120)));
  }

  /** No retries at all: every failure is final. */
  public static RetryPolicy none() {
    return new RetryPolicy(List.of());
  }

  /**
   * The wait before the next attempt, given how many attempts have already failed (1 after the
   * first failure). Empty when {@code failuresSoFar} is less than 1 or greater than the number of
   * delays.
   */
  public Optional<Duration> delayAfter(int failuresSoFar) {
    if (failuresSoFar < 1) {
      return Optional.empty();
    }
    if (failuresSoFar > delays().size()) {
      return Optional.empty();
    }
    int index = failuresSoFar - 1;
    return Optional.of(delays().get(index));
  }
}
