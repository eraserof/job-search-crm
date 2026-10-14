package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRun;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.AgentRunStatus;
import com.jobcrm.agentic.domain.AgentTurn;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import com.jobcrm.agentic.domain.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link JdbcAgentRunRepository}: a saved {@link AgentRun} and all four {@link
 * AgentTurn} subtypes must come back from {@link JdbcAgentRunRepository#findById} byte-for-byte,
 * including ordered turns, a multi-call {@code Assistant}, and a failed {@code ToolResultTurn}
 * (which proves the whole {@code ToolResult} — including {@code ok=false} and the error — survives
 * the JSON column mapping).
 */
class JdbcAgentRunRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private JdbcAgentRunRepository repo() {
    return new JdbcAgentRunRepository(jdbc(), new ObjectMapper());
  }

  @Test
  void savesAndFindsRunningRunWithNoTurns() {
    AgentRun run = AgentRun.start(AgentName.QUERY, "{\"q\":\"hi\"}", T0);
    repo().save(run);

    Optional<AgentRun> found = repo().findById(run.id());

    assertThat(found).isPresent();
    AgentRun r = found.get();
    assertThat(r.id()).isEqualTo(run.id());
    assertThat(r.agent()).isEqualTo(AgentName.QUERY);
    assertThat(r.status()).isEqualTo(AgentRunStatus.RUNNING);
    assertThat(r.startedAt()).isEqualTo(T0);
    assertThat(r.inputJson()).isEqualTo("{\"q\":\"hi\"}");
    assertThat(r.finishedAt()).isEmpty();
    assertThat(r.finalOutput()).isEmpty();
    assertThat(r.errorMessage()).isEmpty();
    assertThat(r.tokens()).isEqualTo(TokenUsage.ZERO);
    assertThat(r.turns()).isEmpty();
  }

  @Test
  void savesAndFindsAllFourTurnTypesInOrder() {
    AgentRun run = AgentRun.start(AgentName.DRAFT_REPLY, "{}", T0);

    ToolCall callA = new ToolCall("call-a", "readOpportunity", "{\"id\":\"opp-1\"}");
    ToolCall callB = new ToolCall("call-b", "listInteractions", "{\"id\":\"opp-1\"}");

    run.appendTurn(new AgentTurn.System(0, T0, "you are the draft agent"));
    run.appendTurn(new AgentTurn.User(1, T0.plusSeconds(1), "draft a thank-you"));
    run.appendTurn(
        new AgentTurn.Assistant(2, T0.plusSeconds(2), "let me look", List.of(callA, callB)));
    run.appendTurn(
        new AgentTurn.ToolResultTurn(
            3, T0.plusSeconds(3), callA, ToolResult.ok("{\"stage\":\"PANEL\"}")));
    run.appendTurn(
        new AgentTurn.ToolResultTurn(
            4, T0.plusSeconds(4), callB, ToolResult.failure("tool blew up")));
    run.finish("here is your draft", new TokenUsage(100, 50), T0.plusSeconds(5));

    repo().save(run);

    AgentRun r = repo().findById(run.id()).orElseThrow();

    assertThat(r.status()).isEqualTo(AgentRunStatus.SUCCESS);
    assertThat(r.finishedAt()).contains(T0.plusSeconds(5));
    assertThat(r.finalOutput()).contains("here is your draft");
    assertThat(r.tokens()).isEqualTo(new TokenUsage(100, 50));

    List<AgentTurn> turns = r.turns();
    assertThat(turns).hasSize(5);

    // Order preserved by turn_index.
    assertThat(turns).extracting(AgentTurn::index).containsExactly(0, 1, 2, 3, 4);

    assertThat(turns.get(0)).isInstanceOf(AgentTurn.System.class);
    assertThat(((AgentTurn.System) turns.get(0)).content()).isEqualTo("you are the draft agent");

    assertThat(turns.get(1)).isInstanceOf(AgentTurn.User.class);
    assertThat(((AgentTurn.User) turns.get(1)).content()).isEqualTo("draft a thank-you");

    AgentTurn.Assistant a = (AgentTurn.Assistant) turns.get(2);
    assertThat(a.contentJson()).isEqualTo("let me look");
    assertThat(a.toolCalls()).containsExactly(callA, callB);

    AgentTurn.ToolResultTurn okTurn = (AgentTurn.ToolResultTurn) turns.get(3);
    assertThat(okTurn.call()).isEqualTo(callA);
    assertThat(okTurn.result().ok()).isTrue();
    assertThat(okTurn.result().resultJson()).isEqualTo("{\"stage\":\"PANEL\"}");

    AgentTurn.ToolResultTurn failTurn = (AgentTurn.ToolResultTurn) turns.get(4);
    assertThat(failTurn.call()).isEqualTo(callB);
    assertThat(failTurn.result().ok()).isFalse();
    assertThat(failTurn.result().errorMessageOpt()).contains("tool blew up");
  }

  @Test
  void saveTwiceUpdatesRunInPlaceAndDoesNotDuplicateTurns() {
    AgentRun run = AgentRun.start(AgentName.QUERY, "{}", T0);
    run.appendTurn(new AgentTurn.System(0, T0, "system prompt"));
    repo().save(run);

    // Progress the run and save again — same id, more state.
    run.appendTurn(new AgentTurn.User(1, T0.plusSeconds(1), "a question"));
    run.finish("answer", new TokenUsage(10, 5), T0.plusSeconds(2));
    repo().save(run);

    AgentRun r = repo().findById(run.id()).orElseThrow();
    assertThat(r.status()).isEqualTo(AgentRunStatus.SUCCESS);
    assertThat(r.turns()).hasSize(2);
    assertThat(countRows("agent_run")).isEqualTo(1);
    assertThat(countRows("agent_turn")).isEqualTo(2);

    // The mutable fields set by finish() must survive the second save (the ON CONFLICT UPDATE
    // has to refresh final_output / finished_at / tokens, not just status).
    assertThat(r.finalOutput()).contains("answer");
    assertThat(r.finishedAt()).contains(T0.plusSeconds(2));
    assertThat(r.tokens()).isEqualTo(new TokenUsage(10, 5));
  }

  @Test
  void failedRunPersistsErrorAndFinishedAt() {
    AgentRun run = AgentRun.start(AgentName.EMAIL_INGEST, "{}", T0);
    run.fail("policy_violation", T0.plusSeconds(1));
    repo().save(run);

    AgentRun r = repo().findById(run.id()).orElseThrow();
    assertThat(r.status()).isEqualTo(AgentRunStatus.FAILED);
    assertThat(r.errorMessage()).contains("policy_violation");
    assertThat(r.finishedAt()).contains(T0.plusSeconds(1));
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(AgentRunId.newId())).isEmpty();
  }
}
