package com.jobcrm.core.domain;

import static com.jobcrm.core.domain.Stage.APPLIED;
import static com.jobcrm.core.domain.Stage.GHOSTED;
import static com.jobcrm.core.domain.Stage.HM_SCREEN;
import static com.jobcrm.core.domain.Stage.OFFER;
import static com.jobcrm.core.domain.Stage.ONSITE;
import static com.jobcrm.core.domain.Stage.PANEL;
import static com.jobcrm.core.domain.Stage.RECRUITER_SCREEN;
import static com.jobcrm.core.domain.Stage.REJECTED;
import static com.jobcrm.core.domain.Stage.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Truth-table style tests for {@link Stage}. Deliberately encodes the allowed transitions twice —
 * once in {@code Stage.canTransitionTo} and once here — so that the test fails if either drifts.
 * The exhaustive N-by-N loop gives 100% branch coverage of the state machine's switch expression.
 */
class StageTest {

  /** Allowed transitions per stage. Source of truth for this test only. */
  private static final Map<Stage, Set<Stage>> ALLOWED =
      Map.of(
          APPLIED, Set.of(RECRUITER_SCREEN, HM_SCREEN, REJECTED, GHOSTED, WITHDRAWN),
          RECRUITER_SCREEN, Set.of(HM_SCREEN, PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN),
          HM_SCREEN, Set.of(PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN),
          PANEL, Set.of(ONSITE, OFFER, REJECTED, GHOSTED, WITHDRAWN),
          ONSITE, Set.of(OFFER, REJECTED, GHOSTED, WITHDRAWN),
          OFFER, Set.of(REJECTED, WITHDRAWN),
          REJECTED, Set.of(),
          GHOSTED, Set.of(RECRUITER_SCREEN, HM_SCREEN),
          WITHDRAWN, Set.of());

  @Test
  @DisplayName("every (from, to) pair matches the truth table")
  void every_pair_matches_the_truth_table() {
    for (Stage from : Stage.values()) {
      for (Stage to : Stage.values()) {
        boolean expected = ALLOWED.get(from).contains(to);
        assertThat(from.canTransitionTo(to)).as("transition %s → %s", from, to).isEqualTo(expected);
      }
    }
  }

  @Test
  @DisplayName("canTransitionTo(null) returns false rather than throwing")
  void null_target_returns_false() {
    for (Stage from : Stage.values()) {
      assertThat(from.canTransitionTo(null)).as("from = %s", from).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(names = {"REJECTED", "WITHDRAWN"})
  void terminal_stages_report_terminal(Stage stage) {
    assertThat(stage.isTerminal()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      mode = EnumSource.Mode.EXCLUDE,
      names = {"REJECTED", "WITHDRAWN"})
  void non_terminal_stages_report_non_terminal(Stage stage) {
    assertThat(stage.isTerminal()).isFalse();
  }

  @Test
  @DisplayName("ghosted is not terminal — re-engagement is allowed")
  void ghosted_is_not_terminal() {
    assertThat(GHOSTED.isTerminal()).isFalse();
    assertThat(GHOSTED.canTransitionTo(RECRUITER_SCREEN)).isTrue();
    assertThat(GHOSTED.canTransitionTo(HM_SCREEN)).isTrue();
  }

  @Test
  @DisplayName("no stage transitions to itself")
  void no_self_transitions() {
    for (Stage stage : Stage.values()) {
      assertThat(stage.canTransitionTo(stage))
          .as("self-transition %s → %s", stage, stage)
          .isFalse();
    }
  }
}
