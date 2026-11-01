package com.jobcrm.agentic.application;

import com.jobcrm.agentic.domain.AgentDefinition;
import com.jobcrm.agentic.domain.AgentRun;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.AgentRunOutcome;
import com.jobcrm.agentic.domain.AgentRunRepository;
import com.jobcrm.agentic.domain.AgentTool;
import com.jobcrm.agentic.domain.AgentTurn;
import com.jobcrm.agentic.domain.AgentTurn.ToolResultTurn;
import com.jobcrm.agentic.domain.LlmClient;
import com.jobcrm.agentic.domain.LlmMessage;
import com.jobcrm.agentic.domain.LlmModelParams;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import com.jobcrm.agentic.domain.Role;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import com.jobcrm.agentic.domain.ToolContext;
import com.jobcrm.agentic.domain.ToolRegistry;
import com.jobcrm.agentic.domain.ToolResult;
import com.jobcrm.agentic.domain.ToolSpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class AgentRunner {
  private final ToolRegistry tools;
  private final AgentRunRepository runs;
  private final Clock clock;
  private final ToolContextFactory contextFactory;

  public AgentRunner(
      ToolRegistry tools, AgentRunRepository runs, Clock clock, ToolContextFactory contextFactory) {
    this.tools = tools;
    this.runs = runs;
    this.clock = clock;
    this.contextFactory = contextFactory;
  }

  public AgentRunOutcome run(AgentRunId id, AgentDefinition def, String inputJson) {
    // Dispatcher will call this run function

    if (def == null) {
      throw new AgentRunnerException("AgentDefinition cannot be null");
    } else if (inputJson == null || inputJson.isBlank()) {
      throw new AgentRunnerException("Input Json cannot be null");
    }

    AgentRun run = AgentRun.start(id, def.name(), inputJson, clock.instant());

    LlmModelParams params = def.params();
    List<ToolSpec> toolSpecs = tools.specs(def.allowedToolNames());
    ToolContext context = contextFactory.create(run.id(), run.agent());
    // There is no tool id associated with the user prompt
    List<LlmMessage> history =
        new ArrayList<>(List.of(new LlmMessage(Role.USER, inputJson, List.of(), Optional.empty())));
    LlmClient llmClient = def.llmClient();
    TokenUsage totalTokens = TokenUsage.ZERO;

    for (int step = 0; step < def.maxSteps(); step++) {
      LlmRequest request = new LlmRequest(def.systemPrompt(), history, toolSpecs, params);
      LlmResponse response = llmClient.complete(request);
      totalTokens = totalTokens.add(response.tokens());
      run.appendTurn(
          new AgentTurn.Assistant(
              step, clock.instant(), response.finalText().orElse(""), response.toolCalls()));
      runs.save(run);
      // There should be one message in the response
      history.addAll(response.newMessages());
      if (response.toolCalls().isEmpty()) {
        // An empty final answer isnt a failed answer. We successfully called the tools
        // and performed something.
        String finalText = response.finalText().orElse("");
        run.finish(finalText, totalTokens, clock.instant());
        runs.save(run);
        return new AgentRunOutcome.Success(run.id(), finalText);
      } else {
        for (ToolCall tool : response.toolCalls()) {
          if (!def.allowedToolNames().contains(tool.name())) {
            run.fail("Tool not allowed", clock.instant());
            runs.save(run);
            return new AgentRunOutcome.Failed(run.id(), "Tool not allowed");
          }
          AgentTool agentTool = tools.byName(tool.name());
          ToolResult result = agentTool.execute(tool.argsJson(), context);
          ToolResultTurn toolResultTurn =
              new AgentTurn.ToolResultTurn(step, clock.instant(), tool, result);
          run.appendTurn(toolResultTurn);
          // Tool Result doesnt have a toolCalls. It doesnt request tools
          history.add(
              new LlmMessage(
                  Role.TOOL,
                  toolResultTurn.result().resultJson(),
                  List.of(),
                  Optional.of(tool.id())));
        }
      }
    }
    run.abort("Ran out of steps", clock.instant());
    runs.save(run);
    return new AgentRunOutcome.Aborted(run.id(), "Ran out of steps.");
  }

  public AgentRunOutcome run(AgentDefinition def, String inputJson) {
    return run(AgentRunId.newId(), def, inputJson);
  }
}
