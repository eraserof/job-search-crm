package com.jobcrm.codegen.metrics;

import static com.jobcrm.codegen.metrics.StageStatsTest.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatsReportTest {

  private static final String HEADER =
      "stage calls failures prompt_total prompt_avg completion_total completion_avg avg_ms max_ms"
          + " ctps";

  @Test
  void oneRowPerStageSortedByStageName() {
    List<CallRecord> records =
        List.of(
            record("repair", 1000, 500, 10_000, true),
            record("code", 100, 40, 2_000, true),
            record("code", 300, 60, 6_000, false));

    assertThat(StatsReport.report(records))
        .isEqualTo(
            HEADER
                + "\n"
                + "code 2 1 400 200.0 100 50.0 4000.0 6000 12.50\n"
                + "repair 1 0 1000 1000.0 500 500.0 10000.0 10000 50.00\n");
  }

  @Test
  void anEmptyListIsTheHeaderOnly() {
    assertThat(StatsReport.report(List.of())).isEqualTo(HEADER);
  }

  @Test
  void aNullListIsRejected() {
    assertThatThrownBy(() -> StatsReport.report(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("records");
  }
}
