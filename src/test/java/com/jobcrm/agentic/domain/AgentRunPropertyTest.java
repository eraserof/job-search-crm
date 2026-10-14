package com.jobcrm.agentic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based coverage of the two {@link AgentRun} aggregate invariants named in Task 12:
 *
 * <ul>
 *   <li>appended turns preserve their order (the turn list reflects append order), and
 *   <li>the run cannot be mutated after it reaches a terminal state ({@code finish}/{@code
 *       fail}/{@code abort}) — any further lifecycle call throws {@link IllegalAgentRunState}.
 * </ul>
 */
class AgentRunPropertyTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @Provide
  Arbitrary<AgentName> agents() {
    return Arbitraries.of(AgentName.values());
  }

  @Property(tries = 50)
  void appendedTurnsPreserveOrder(
      @ForAll("agents") AgentName agent, @ForAll @IntRange(min = 0, max = 20) int count) {
    AgentRun run = AgentRun.start(agent, "{}", T0);

    List<Integer> expectedIndexes = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      run.appendTurn(new AgentTurn.System(i, T0.plusSeconds(i), "turn " + i));
      expectedIndexes.add(i);
    }

    assertThat(run.turns()).extracting(AgentTurn::index).containsExactlyElementsOf(expectedIndexes);
  }

  @Property(tries = 50)
  void cannotMutateAfterTerminalState(
      @ForAll("agents") AgentName agent, @ForAll @IntRange(min = 0, max = 2) int terminalChoice) {
    AgentRun run = AgentRun.start(agent, "{}", T0);
    run.appendTurn(new AgentTurn.System(0, T0, "a turn before finishing"));

    // Drive the run to one of the three terminal states.
    switch (terminalChoice) {
      case 0 -> run.finish("done", new TokenUsage(1, 1), T0.plusSeconds(1));
      case 1 -> run.fail("boom", T0.plusSeconds(1));
      default -> run.abort("max_steps", T0.plusSeconds(1));
    }

    AgentRunStatus terminal = run.status();
    assertThat(terminal).isNotEqualTo(AgentRunStatus.RUNNING);

    // Every lifecycle operation must now be rejected.
    assertThatExceptionOfType(IllegalAgentRunState.class)
        .isThrownBy(() -> run.appendTurn(new AgentTurn.User(1, T0.plusSeconds(2), "late turn")));
    assertThatExceptionOfType(IllegalAgentRunState.class)
        .isThrownBy(() -> run.finish("again", TokenUsage.ZERO, T0.plusSeconds(2)));
    assertThatExceptionOfType(IllegalAgentRunState.class)
        .isThrownBy(() -> run.fail("again", T0.plusSeconds(2)));
    assertThatExceptionOfType(IllegalAgentRunState.class)
        .isThrownBy(() -> run.abort("again", T0.plusSeconds(2)));

    // State is unchanged by the rejected calls.
    assertThat(run.status()).isEqualTo(terminal);
    assertThat(run.turns()).hasSize(1);
  }

  @Property(tries = 30)
  void turnsListIsUnmodifiable(@ForAll("agents") AgentName agent) {
    AgentRun run = AgentRun.start(agent, "{}", T0);
    run.appendTurn(new AgentTurn.System(0, T0, "only turn"));

    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> run.turns().add(new AgentTurn.User(1, T0, "sneaky")));
  }
}
