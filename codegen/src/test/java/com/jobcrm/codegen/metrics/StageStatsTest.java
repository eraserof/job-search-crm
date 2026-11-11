package com.jobcrm.codegen.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class StageStatsTest {

  @Test
  void summarizesCountsTotalsAveragesMaxAndThroughput() {
    List<CallRecord> records =
        List.of(
            record("code", 100, 40, 2_000, true),
            record("code", 300, 60, 6_000, false),
            record("code", 200, 50, 4_000, true));

    StageStats s = StageStats.from("code", records);

    assertThat(s.stage()).isEqualTo("code");
    assertThat(s.calls()).isEqualTo(3);
    assertThat(s.failures()).isEqualTo(1);
    assertThat(s.promptTokensTotal()).isEqualTo(600);
    assertThat(s.promptTokensAverage()).isEqualTo(200.0);
    assertThat(s.completionTokensTotal()).isEqualTo(150);
    assertThat(s.completionTokensAverage()).isEqualTo(50.0);
    assertThat(s.totalTimeMillisAverage()).isEqualTo(4_000.0);
    assertThat(s.totalTimeMillisMax()).isEqualTo(6_000);
    // 150 completion tokens over 12 seconds in total
    assertThat(s.completionTokensPerSecond()).isCloseTo(12.5, within(1e-9));
  }

  @Test
  void zeroTotalTimeMeansZeroThroughputNotDivisionByZero() {
    StageStats s = StageStats.from("plan", List.of(record("plan", 10, 5, 0, true)));

    assertThat(s.completionTokensPerSecond()).isZero();
    assertThat(s.totalTimeMillisMax()).isZero();
  }

  @Test
  void rejectsMissingOrEmptyInput() {
    assertThatThrownBy(() -> StageStats.from(null, List.of(record("x", 1, 1, 1, true))))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("stage");
    assertThatThrownBy(() -> StageStats.from("x", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("records");
    assertThatThrownBy(() -> StageStats.from("x", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("records must not be empty");
  }

  static CallRecord record(
      String stage, long promptTokens, long completionTokens, long totalMillis, boolean success) {
    return new CallRecord(
        stage,
        "m",
        "p",
        null,
        promptTokens,
        completionTokens,
        0,
        0,
        totalMillis,
        success,
        "2026-11-07T12:00:00Z");
  }
}
