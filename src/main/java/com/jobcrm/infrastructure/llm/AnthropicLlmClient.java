package com.jobcrm.infrastructure.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobcrm.agentic.domain.LlmClient;
import com.jobcrm.agentic.domain.LlmMessage;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import com.jobcrm.agentic.domain.Role;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import com.jobcrm.agentic.domain.ToolSpec;
import com.jobcrm.infrastructure.config.JobcrmConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AnthropicLlmClient implements LlmClient {

  // THere is a JobcrmConfig object that has the LLM values we need
  private final String apiKey;
  private final String url;
  private final String anthropicVersion;
  private final HttpClient client;
  private final ObjectMapper json;

  public AnthropicLlmClient(ObjectMapper json, JobcrmConfig config) {
    this.json = json;
    this.apiKey = config.llm().apiKey();
    this.anthropicVersion = AnthropicLlmVersions.LATEST.getValue();
    this.url = "https://api.anthropic.com/v1/messages";
    this.client = HttpClient.newHttpClient();
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    String bodyJson = buildBody(request);
    HttpRequest httpRequest = buildHttpRequest(bodyJson);
    HttpResponse<String> response;
    try {
      response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new LlmClientException("There was an issue sending request to Anthropic", e);
    } catch (InterruptedException e) {
      throw new LlmClientException("There was an issue sending request to Anthropic", e);
    }
    if (response.statusCode() / 100 != 2) {
      throw new LlmClientException(
          "Anthropic returned " + response.statusCode() + ": " + response.body());
    }
    return parseResponse(response.body());
  }

  private HttpRequest buildHttpRequest(String body, String contentString) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-api-key", apiKey)
            .header("anthropic-version", anthropicVersion)
            .header("content-type", contentString)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return request;
  }

  private HttpRequest buildHttpRequest(String body) {
    return this.buildHttpRequest(body, "application/json");
  }

  private String buildBody(LlmRequest request) {
    ObjectNode body = json.createObjectNode();
    body.put("model", request.params().modelId());
    body.put("max_tokens", request.params().maxTokens());
    body.put("temperature", request.params().temperature());

    // system is OPTIONAL — just don't add it when absent. No nullable field, no
    // annotation.
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      body.put("system", request.systemPrompt());
    }

    // top_k / top_p — only add if present
    request.params().topK().ifPresent(k -> body.put("top_k", k));
    request.params().topP().ifPresent(p -> body.put("top_p", p));

    ArrayNode messages = json.createArrayNode();
    for (LlmMessage m : request.history()) {
      if (m.role() == Role.SYSTEM) {
        continue; // system prompt is the top-level field, not a message
      }
      ObjectNode msg = json.createObjectNode();
      msg.put("role", mapRole(m)); // "user" or "assistant"
      // Content value is depends if it is a tool call or not.
      if (m.role() == Role.USER && m.toolCallId().isEmpty()) {
        msg.put("content", m.content()); // content is a String
      } else {
        // TODO Task 14: this branch mishandles two cases that only matter once the runner
        // replays real multi-turn history:
        //   1. ASSISTANT messages should emit their text as string content, not an empty array —
        //      a plain assistant turn would currently serialize with no content.
        //   2. TOOL-role messages should emit a tool_result block array referencing toolCallId.
        // Not exercised in Task 13 (tested via Mock/Recorded clients); left as a stub until the
        // AgentRunner feeds conversation history through.
        ArrayNode blocks = json.createArrayNode();
        msg.set("content", blocks); // content is an array
      }
      messages.add(msg);
    }
    body.set("messages", messages);

    ArrayNode tools = json.createArrayNode();
    for (ToolSpec spec : request.availableTools()) {
      ObjectNode tool = json.createObjectNode();
      tool.put("name", spec.name());
      tool.put("description", spec.description());
      // spec.jsonSchema() is a JSON STRING; parse it into a node so it embeds as an
      try {
        tool.set("input_schema", json.readTree(spec.jsonSchema()));
      } catch (JsonProcessingException e) {
        throw new LlmJsonException("Issue with reading tool spec", e);
      }
      tools.add(tool);
    }
    if (!tools.isEmpty()) {
      body.set("tools", tools);
    }

    try {
      return json.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new LlmJsonException("Issue writing body as string", e);
    }
  }

  private String mapRole(LlmMessage m) {
    return switch (m.role()) {
      case ASSISTANT -> "assistant";
      case USER, TOOL -> "user";
      case SYSTEM -> throw new LlmJsonException("SYSTEM message should not be in history", null);
    };
  }

  // We need to read the reponse by parsing the response.
  //
  /*
   * {
   * "id": "msg_...",
   * "type": "message",
   * "role": "assistant",
   * "model": "claude-sonnet-4-5",
   * "content": [ /* array of content blocks],
   * "stop_reason":"end_turn","stop_sequence":null,"usage":
   * { "input_tokens": 1234, "output_tokens": 87 }}
   */

  private LlmResponse parseResponse(String body) {
    JsonNode root;
    try {
      root = json.readTree(body);
    } catch (JsonProcessingException e) {
      throw new LlmJsonException("Error parsing LLM response", e);
    }
    JsonNode content = root.path("content"); // the array of blocks
    List<ToolCall> toolCalls = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for (JsonNode block : content) {
      String type = block.path("type").asText();
      /*
       *
       * text block:
       * { "type": "text", "text": "Let me look that up." }
       * Or can be a tool_use block:
       * tool_use block (the model is requesting a tool call):
       * {
       * "type": "tool_use",
       * "id": "toolu_01A...",
       * "name": "searchOpportunities",
       * "input": { "query": "Acme" }
       * }
       */
      if (type.equals("text")) {
        /* block.path("text").asText() */
        sb.append(block.path("text").asText());
      } else if (type.equals("tool_use")) {
        /* block.path("id"), path("name"), path("input") */
        toolCalls.add(
            new ToolCall(
                block.path("id").asText(),
                block.path("name").asText(),
                writeJson(block.path("input"))));
      }
    }
    String toolText = sb.toString();
    List<ToolCall> calls = toolCalls;
    Optional<String> finalText = calls.isEmpty() ? Optional.of(toolText) : Optional.empty();

    long inputTokens = root.path("usage").path("input_tokens").asLong();
    long outputTokens = root.path("usage").path("output_tokens").asLong();

    LlmMessage message = new LlmMessage(Role.ASSISTANT, toolText, toolCalls, Optional.empty());

    return new LlmResponse(
        List.of(message), calls, finalText, new TokenUsage(inputTokens, outputTokens), body);
  }

  private String writeJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new LlmJsonException("JSON write failed", e);
    }
  }
}
