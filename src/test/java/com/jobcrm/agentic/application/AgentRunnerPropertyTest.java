package com.jobcrm.agentic.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.agentic.application.AgentRunnerTest.InMemoryAgentRunRepository;
import com.jobcrm.agentic.domain.AgentDefinition;
import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRun;
import com.jobcrm.agentic.domain.AgentRunOutcome;
import com.jobcrm.agentic.domain.AgentRunStatus;
import com.jobcrm.agentic.domain.AgentTool;
import com.jobcrm.agentic.domain.AgentTurn;
import com.jobcrm.agentic.domain.LlmClient;
import com.jobcrm.agentic.domain.LlmMessage;
import com.jobcrm.agentic.domain.LlmModelParams;
import com.jobcrm.agentic.domain.LlmResponse;
import com.jobcrm.agentic.domain.Role;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import com.jobcrm.agentic.domain.ToolContext;
import com.jobcrm.agentic.domain.ToolRegistry;
import com.jobcrm.agentic.domain.ToolResult;
import com.jobcrm.agentic.domain.ToolSpec;
import com.jobcrm.infrastructure.llm.MockLlmClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based coverage of the runner's correctness properties (agent-architecture.md):
 *
 * <ul>
 *   <li>every run produces a terminal {@link AgentRunStatus} (SUCCESS / FAILED / ABORTED), and
 *   <li>the persisted turn count matches the number of assistant messages the stub was asked to
 *       produce (one assistant turn per LLM call, plus one tool-result turn per tool-call turn).
 * </ul>
 */
class AgentRunnerPropertyTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String TOOL = "readOpportunity";

  @Property(tries = 50)
  void everyRunEndsInATerminalStatus(
      @ForAll @IntRange(min = 1, max = 8) int maxSteps,
      @ForAll @IntRange(min = 0, max = 10) int toolTurnsBeforeFinal) {
    // Script: `toolTurnsBeforeFinal` tool-requesting turns, then a final answer. Depending on
    // maxSteps, the run will either finish (Success) or run out of steps (Aborted).
    MockLlmClient llm = new MockLlmClient();
    for (int i = 0; i < toolTurnsBeforeFinal; i++) {
      llm.enqueue(wantsTool());
    }
    // Only enqueue the final answer if the loop could actually reach it, otherwise the mock would
    // run dry inside the loop — but since the loop stops at maxSteps, enqueue enough to be safe.
    llm.enqueue(finalAnswer());

    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();
    AgentRunner runner =
        new AgentRunner(
            new StubRegistry(),
            repo,
            CLOCK,
            new ToolContextFactory(CLOCK, null, null, null, null, null, null, null));

    AgentRunOutcome outcome = runner.run(new StubDef(llm, maxSteps), "{}");

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    assertThat(saved.status())
        .isIn(AgentRunStatus.SUCCESS, AgentRunStatus.FAILED, AgentRunStatus.ABORTED);
    assertThat(saved.status()).isNotEqualTo(AgentRunStatus.RUNNING);
  }

  @Property(tries = 50)
  void persistedAssistantTurnCountMatchesLlmCalls(
      @ForAll @IntRange(min = 1, max = 8) int maxSteps) {
    // Always ask for a tool, never finish, so the run aborts exactly at maxSteps. That means
    // exactly `maxSteps` LLM calls → exactly `maxSteps` assistant turns recorded.
    MockLlmClient llm = new MockLlmClient();
    for (int i = 0; i < maxSteps; i++) {
      llm.enqueue(wantsTool());
    }

    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();
    AgentRunner runner =
        new AgentRunner(
            new StubRegistry(),
            repo,
            CLOCK,
            new ToolContextFactory(CLOCK, null, null, null, null, null, null, null));

    AgentRunOutcome outcome = runner.run(new StubDef(llm, maxSteps), "{}");

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    long assistantTurns =
        saved.turns().stream().filter(t -> t instanceof AgentTurn.Assistant).count();
    assertThat(assistantTurns).isEqualTo(maxSteps);
    assertThat(llm.callCount()).isEqualTo(maxSteps);
  }

  // ---- helpers ----

  private static LlmResponse wantsTool() {
    ToolCall call = new ToolCall("c", TOOL, "{}");
    LlmMessage msg = new LlmMessage(Role.ASSISTANT, "", List.of(call), Optional.empty());
    return new LlmResponse(
        List.of(msg), List.of(call), Optional.empty(), new TokenUsage(1, 1), "{}");
  }

  private static LlmResponse finalAnswer() {
    LlmMessage msg = new LlmMessage(Role.ASSISTANT, "done", List.of(), Optional.empty());
    return new LlmResponse(
        List.of(msg), List.of(), Optional.of("done"), new TokenUsage(1, 1), "{}");
  }

  private static final class StubRegistry implements ToolRegistry {
    @Override
    public AgentTool byName(String name) {
      return new AgentTool() {
        @Override
        public String name() {
          return TOOL;
        }

        @Override
        public String description() {
          return "stub";
        }

        @Override
        public String jsonSchema() {
          return "{}";
        }

        @Override
        public ToolResult execute(String argsJson, ToolContext ctx) {
          return ToolResult.ok("{}");
        }
      };
    }

    @Override
    public List<ToolSpec> specs(Collection<String> names) {
      return names.stream().map(n -> new ToolSpec(n, "stub", "{}")).toList();
    }
  }

  private static final class StubDef implements AgentDefinition {
    private final LlmClient client;
    private final int maxSteps;

    StubDef(LlmClient client, int maxSteps) {
      this.client = client;
      this.maxSteps = maxSteps;
    }

    @Override
    public AgentName name() {
      return AgentName.QUERY;
    }

    @Override
    public String systemPrompt() {
      return "test";
    }

    @Override
    public Set<String> allowedToolNames() {
      return Set.of(TOOL);
    }

    @Override
    public int maxSteps() {
      return maxSteps;
    }

    @Override
    public LlmClient llmClient() {
      return client;
    }

    @Override
    public LlmModelParams params() {
      return new LlmModelParams("m", 0.2, 1000, Optional.empty(), Optional.empty());
    }
  }
}
