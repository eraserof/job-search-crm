package com.jobcrm.agentic.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.agentic.domain.AgentDefinition;
import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRun;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.AgentRunOutcome;
import com.jobcrm.agentic.domain.AgentRunRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Control-flow unit tests for {@link AgentRunner}, driving a scripted {@link MockLlmClient} through
 * the execution loop. The runner is constructed directly (no dispatcher, no mutex, no threads) per
 * the spec's testability note, so every branch — success, max-steps abort, disallowed-tool failure,
 * token accumulation, turn recording — is exercised deterministically.
 */
class AgentRunnerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String TOOL_NAME = "readOpportunity";

  // ---- responses the mock can return ----

  private static LlmResponse finalAnswer(String text, TokenUsage tokens) {
    LlmMessage msg = new LlmMessage(Role.ASSISTANT, text, List.of(), Optional.empty());
    return new LlmResponse(List.of(msg), List.of(), Optional.of(text), tokens, "{}");
  }

  private static LlmResponse wantsTool(String toolName, TokenUsage tokens) {
    ToolCall call = new ToolCall("call-1", toolName, "{}");
    LlmMessage msg = new LlmMessage(Role.ASSISTANT, "", List.of(call), Optional.empty());
    return new LlmResponse(List.of(msg), List.of(call), Optional.empty(), tokens, "{}");
  }

  // ---- the system under test, assembled from doubles ----

  private AgentRunner runner(MockLlmClient llm, Set<String> allowedTools, AgentRunRepository repo) {
    ToolRegistry registry = new StubToolRegistry(TOOL_NAME);
    // Stub tools ignore the ToolContext, so a factory with null repos is fine here.
    ToolContextFactory contextFactory =
        new ToolContextFactory(CLOCK, null, null, null, null, null, null, null);
    return new AgentRunner(registry, repo, CLOCK, contextFactory);
  }

  private AgentDefinition def(MockLlmClient llm, int maxSteps, Set<String> allowedTools) {
    return new StubAgentDefinition(llm, maxSteps, allowedTools);
  }

  // ---- tests ----

  @Test
  void finalAnswerOnFirstTurnReturnsSuccess() {
    MockLlmClient llm =
        new MockLlmClient().enqueue(finalAnswer("here it is", new TokenUsage(10, 5)));
    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();

    AgentRunOutcome outcome =
        runner(llm, Set.of(TOOL_NAME), repo).run(def(llm, 5, Set.of(TOOL_NAME)), "{}");

    assertThat(outcome).isInstanceOf(AgentRunOutcome.Success.class);
    assertThat(((AgentRunOutcome.Success) outcome).finalText()).isEqualTo("here it is");

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    assertThat(saved.status()).isEqualTo(AgentRunStatus.SUCCESS);
    assertThat(saved.finalOutput()).contains("here it is");
  }

  @Test
  void toolThenFinalAnswerReturnsSuccessAndRecordsAllTurns() {
    MockLlmClient llm =
        new MockLlmClient()
            .enqueue(
                wantsTool(TOOL_NAME, new TokenUsage(100, 20)),
                finalAnswer("done", new TokenUsage(120, 40)));
    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();

    AgentRunOutcome outcome =
        runner(llm, Set.of(TOOL_NAME), repo).run(def(llm, 5, Set.of(TOOL_NAME)), "{}");

    assertThat(outcome).isInstanceOf(AgentRunOutcome.Success.class);

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    // Turn 0: assistant asks for a tool; tool result; turn 1: assistant final answer.
    assertThat(saved.turns()).hasSize(3).anyMatch(t -> t instanceof AgentTurn.ToolResultTurn);
  }

  @Test
  void disallowedToolReturnsFailedAndMarksRunFailed() {
    // The model asks for a tool that is NOT in the agent's allow-list.
    MockLlmClient llm =
        new MockLlmClient().enqueue(wantsTool("sendMessage", new TokenUsage(10, 5)));
    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();

    AgentRunOutcome outcome =
        runner(llm, Set.of(TOOL_NAME), repo).run(def(llm, 5, Set.of(TOOL_NAME)), "{}");

    assertThat(outcome).isInstanceOf(AgentRunOutcome.Failed.class);

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    assertThat(saved.status()).isEqualTo(AgentRunStatus.FAILED);
    // Even on failure, the assistant turn that requested the tool was recorded.
    assertThat(saved.turns()).isNotEmpty();
  }

  @Test
  void maxStepsExhaustedReturnsAborted() {
    // Every turn asks for a tool and never gives a final answer, so the loop runs out of steps.
    MockLlmClient llm =
        new MockLlmClient()
            .enqueue(
                wantsTool(TOOL_NAME, new TokenUsage(1, 1)),
                wantsTool(TOOL_NAME, new TokenUsage(1, 1)));
    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();

    AgentRunOutcome outcome =
        runner(llm, Set.of(TOOL_NAME), repo).run(def(llm, 2, Set.of(TOOL_NAME)), "{}");

    assertThat(outcome).isInstanceOf(AgentRunOutcome.Aborted.class);

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    assertThat(saved.status()).isEqualTo(AgentRunStatus.ABORTED);
    // maxSteps = 2 → two LLM calls were made.
    assertThat(llm.callCount()).isEqualTo(2);
  }

  @Test
  void accumulatesTokensAcrossTurns() {
    MockLlmClient llm =
        new MockLlmClient()
            .enqueue(
                wantsTool(TOOL_NAME, new TokenUsage(100, 20)),
                finalAnswer("done", new TokenUsage(50, 10)));
    InMemoryAgentRunRepository repo = new InMemoryAgentRunRepository();

    AgentRunOutcome outcome =
        runner(llm, Set.of(TOOL_NAME), repo).run(def(llm, 5, Set.of(TOOL_NAME)), "{}");

    AgentRun saved = repo.findById(outcome.runId()).orElseThrow();
    // 100+50 input, 20+10 output.
    assertThat(saved.tokens()).isEqualTo(new TokenUsage(150, 30));
  }

  // ---- test doubles ----

  /** A tool that always succeeds and ignores its context. */
  private static final class StubTool implements AgentTool {
    private final String name;

    StubTool(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
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
      return ToolResult.ok("{\"done\":true}");
    }
  }

  /** Registry holding a single stub tool. */
  private static final class StubToolRegistry implements ToolRegistry {
    private final AgentTool tool;

    StubToolRegistry(String toolName) {
      this.tool = new StubTool(toolName);
    }

    @Override
    public AgentTool byName(String name) {
      return tool;
    }

    @Override
    public List<ToolSpec> specs(Collection<String> names) {
      return names.stream().map(n -> new ToolSpec(n, "stub", "{}")).toList();
    }
  }

  /** Minimal agent definition backed by the mock LLM. */
  private static final class StubAgentDefinition implements AgentDefinition {
    private final LlmClient client;
    private final int maxSteps;
    private final Set<String> allowed;

    StubAgentDefinition(LlmClient client, int maxSteps, Set<String> allowed) {
      this.client = client;
      this.maxSteps = maxSteps;
      this.allowed = allowed;
    }

    @Override
    public AgentName name() {
      return AgentName.QUERY;
    }

    @Override
    public String systemPrompt() {
      return "you are a test agent";
    }

    @Override
    public Set<String> allowedToolNames() {
      return allowed;
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
      return new LlmModelParams("test-model", 0.2, 1000, Optional.empty(), Optional.empty());
    }
  }

  /** In-memory {@link AgentRunRepository} for assertions. */
  static final class InMemoryAgentRunRepository implements AgentRunRepository {
    private final Map<AgentRunId, AgentRun> store = new HashMap<>();

    @Override
    public Optional<AgentRun> findById(AgentRunId id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public void save(AgentRun agentRun) {
      store.put(agentRun.id(), agentRun);
    }
  }
}
