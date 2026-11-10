package com.jobcrm.codegen.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallLabelTest {

  @Test
  void hasNoLabelUntilOneIsSet() {
    assertThat(new CallLabel().current()).isNull();
  }

  @Test
  void aScopeSetsTheLabelAndRestoresThePreviousOneOnClose() {
    CallLabel label = new CallLabel();

    try (CallLabel.Scope outer = label.use("plan-x")) {
      assertThat(label.current()).isEqualTo("plan-x");
      try (CallLabel.Scope inner = label.use("A.b")) {
        assertThat(label.current()).isEqualTo("A.b");
      }
      assertThat(label.current()).isEqualTo("plan-x");
    }

    assertThat(label.current()).isNull();
  }

  @Test
  void anExceptionInsideTheScopeStillClearsTheLabel() {
    CallLabel label = new CallLabel();

    try (CallLabel.Scope scope = label.use("A.b")) {
      throw new IllegalStateException("boom");
    } catch (IllegalStateException expected) {
      // the scope was closed on the way out
    }

    assertThat(label.current()).isNull();
  }

  @Test
  void withJobReplacesOnlyTheJob() {
    CallRecord original =
        new CallRecord("code", "m", "p", null, 1, 2, 3, 4, 5, true, "2026-11-09T00:00:00Z");

    CallRecord labeled = original.withJob("A.b");

    assertThat(labeled.job()).isEqualTo("A.b");
    assertThat(labeled).usingRecursiveComparison().ignoringFields("job").isEqualTo(original);
    assertThat(labeled.withJob(null).job()).isNull();
    assertThat(original.job()).isNull();
  }
}
