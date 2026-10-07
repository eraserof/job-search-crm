package com.jobcrm.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@link LogContext} must set MDC keys inside the try-with-resources block and restore the prior
 * state (remove or re-set) on close, even when the keys were nested.
 */
class LogContextTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void key_is_set_inside_and_removed_after_close() {
    assertThat(MDC.get("agentRunId")).isNull();

    try (var ignored = LogContext.with("agentRunId", "run-123")) {
      assertThat(MDC.get("agentRunId")).isEqualTo("run-123");
    }

    assertThat(MDC.get("agentRunId")).isNull();
  }

  @Test
  void multiple_keys_set_and_all_removed_after_close() {
    try (var ignored = LogContext.with("agentRunId", "run-1").and("agentName", "email-ingest")) {
      assertThat(MDC.get("agentRunId")).isEqualTo("run-1");
      assertThat(MDC.get("agentName")).isEqualTo("email-ingest");
    }

    assertThat(MDC.get("agentRunId")).isNull();
    assertThat(MDC.get("agentName")).isNull();
  }

  @Test
  void nested_context_restores_outer_value_not_null() {
    try (var outer = LogContext.with("opportunityId", "opp-outer")) {
      assertThat(MDC.get("opportunityId")).isEqualTo("opp-outer");

      try (var inner = LogContext.with("opportunityId", "opp-inner")) {
        assertThat(MDC.get("opportunityId")).isEqualTo("opp-inner");
      }

      // Closing the inner context restores the outer value, not null.
      assertThat(MDC.get("opportunityId")).isEqualTo("opp-outer");
    }

    assertThat(MDC.get("opportunityId")).isNull();
  }

  @Test
  void null_value_removes_the_key_within_the_context() {
    MDC.put("interactionId", "pre-existing");

    try (var ignored = LogContext.with("interactionId", null)) {
      assertThat(MDC.get("interactionId")).isNull();
    }

    // The prior value is restored on close.
    assertThat(MDC.get("interactionId")).isEqualTo("pre-existing");
  }
}
