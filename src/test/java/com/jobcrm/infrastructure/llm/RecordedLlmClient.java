package com.jobcrm.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.agentic.domain.LlmClient;
import com.jobcrm.agentic.domain.LlmMessage;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import com.jobcrm.agentic.domain.Role;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test {@link LlmClient} that replays a captured fixture: a JSON array of recorded provider
 * responses, returned one per {@code complete} call, keyed by turn index. Used for integration
 * tests where the exact response shape matters but we must never hit a real API in CI
 * (development-guidelines.md § LLM testing).
 *
 * <p>When the fixture is exhausted (more {@code complete} calls than recorded turns), it throws — a
 * loud signal that the agent made more LLM turns than the scenario captured, rather than silently
 * returning stale or empty data.
 *
 * <p>Fixture shape — an array of turns, each mirroring the provider's response essentials:
 *
 * <pre>
 * [
 *   { "text": "let me look", "toolCalls": [ {"id":"t1","name":"readOpportunity","argsJson":"{}"} ],
 *     "inputTokens": 100, "outputTokens": 20 },
 *   { "text": "here is the answer", "toolCalls": [], "inputTokens": 120, "outputTokens": 40 }
 * ]
 * </pre>
 */
public final class RecordedLlmClient implements LlmClient {

  private final List<LlmResponse> turns;
  private int index = 0;

  private RecordedLlmClient(List<LlmResponse> turns) {
    this.turns = turns;
  }

  /** Builds a client from a fixture JSON string (an array of recorded turns). */
  public static RecordedLlmClient fromFixture(String fixtureJson, ObjectMapper json) {
    try {
      JsonNode array = json.readTree(fixtureJson);
      if (!array.isArray()) {
        throw new LlmJsonException("Fixture must be a JSON array of turns");
      }
      List<LlmResponse> parsed = new ArrayList<>();
      for (JsonNode turn : array) {
        parsed.add(toResponse(turn, json));
      }
      return new RecordedLlmClient(parsed);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new LlmJsonException("Could not parse recorded fixture", e);
    }
  }

  private static LlmResponse toResponse(JsonNode turn, ObjectMapper json) {
    String text = turn.path("text").asText("");

    List<ToolCall> toolCalls = new ArrayList<>();
    for (JsonNode call : turn.path("toolCalls")) {
      toolCalls.add(
          new ToolCall(
              call.path("id").asText(),
              call.path("name").asText(),
              call.path("argsJson").asText("{}")));
    }

    Optional<String> finalText = toolCalls.isEmpty() ? Optional.of(text) : Optional.empty();
    TokenUsage tokens =
        new TokenUsage(turn.path("inputTokens").asLong(), turn.path("outputTokens").asLong());
    LlmMessage message = new LlmMessage(Role.ASSISTANT, text, toolCalls, Optional.empty());

    return new LlmResponse(List.of(message), toolCalls, finalText, tokens, turn.toString());
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    if (index >= turns.size()) {
      throw new IllegalStateException(
          "RecordedLlmClient fixture exhausted: only " + turns.size() + " turn(s) recorded");
    }
    return turns.get(index++);
  }
}
