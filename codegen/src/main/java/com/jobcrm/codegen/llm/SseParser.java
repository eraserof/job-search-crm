package com.jobcrm.codegen.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Parses OpenAI-style server-sent-event lines from a streamed chat completion:
 *
 * <pre>
 * data: {"choices":[{"delta":{"content":"Hel"}}]}
 * data: {"choices":[{"delta":{"reasoning_content":"thinking..."}}]}
 * data: [DONE]
 * </pre>
 *
 * Only {@code delta.content} is collected; reasoning deltas are activity but not output.
 */
public final class SseParser {

  private SseParser() {}

  /** True for a {@code data:} line, the only kind that carries an event. */
  public static boolean isData(String line) {
    return line.startsWith("data:");
  }

  /** True for the terminating {@code data: [DONE]} line. */
  public static boolean isDone(String line) {
    return isData(line) && payload(line).equals("[DONE]");
  }

  /**
   * The text in {@code choices[0].delta.content} (or {@code choices[0].message.content}, for
   * servers that ignore {@code stream}). Empty for non-data lines, [DONE], malformed JSON, and
   * events without content.
   */
  public static Optional<String> content(String line, ObjectMapper json) {
    JsonNode event = parse(line, json);
    if (event == null) {
      return Optional.empty();
    }
    JsonNode choice = event.path("choices").path(0);
    JsonNode content = choice.path("delta").path("content");
    if (!content.isTextual()) {
      content = choice.path("message").path("content");
    }
    return content.isTextual() ? Optional.of(content.asText()) : Optional.empty();
  }

  /**
   * The text in {@code choices[0].delta.reasoning_content} (or {@code
   * choices[0].message.reasoning_content}). Kept apart from content; used only as a fallback when a
   * server's reasoning split misfires and the answer ends up in this field.
   */
  public static Optional<String> reasoning(String line, ObjectMapper json) {
    JsonNode event = parse(line, json);
    if (event == null) {
      return Optional.empty();
    }
    JsonNode choice = event.path("choices").path(0);
    JsonNode text = choice.path("delta").path("reasoning_content");
    if (!text.isTextual()) {
      text = choice.path("message").path("reasoning_content");
    }
    return text.isTextual() ? Optional.of(text.asText()) : Optional.empty();
  }

  /** The server's message when the event is an error object ({@code {"error": ...}}). */
  public static Optional<String> error(String line, ObjectMapper json) {
    JsonNode event = parse(line, json);
    if (event == null || !event.has("error")) {
      return Optional.empty();
    }
    JsonNode err = event.get("error");
    String message = err.isTextual() ? err.asText() : err.path("message").asText(err.toString());
    return Optional.of(message);
  }

  private static String payload(String line) {
    return line.substring("data:".length()).strip();
  }

  private static JsonNode parse(String line, ObjectMapper json) {
    if (!isData(line)) {
      return null;
    }
    String payload = payload(line);
    if (payload.isEmpty() || payload.equals("[DONE]")) {
      return null;
    }
    try {
      return json.readTree(payload);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
