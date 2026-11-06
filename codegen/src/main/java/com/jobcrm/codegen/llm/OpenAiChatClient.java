package com.jobcrm.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobcrm.codegen.config.ResolvedStage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Streaming client for OpenAI-compatible {@code /chat/completions} (Ollama, LM Studio).
 *
 * <p>Two timeouts instead of one total timeout, so a slow-but-working model is never killed:
 *
 * <ul>
 *   <li><b>firstTokenTimeout</b>, measured from sending the request until the first {@code data:}
 *       line. This covers waiting behind other requests (parallel = 1 means queueing) and model
 *       loading. Blank keep-alive lines do not extend it.
 *   <li><b>idleTimeout</b>, the longest allowed gap between lines once data has started. Reasoning
 *       deltas count as activity, so a thinking model is not mistaken for a stall.
 * </ul>
 */
public final class OpenAiChatClient implements ChatClient {

  private static final Object END = new Object();
  private static final List<Integer> RETRYABLE_STATUS = List.of(429, 500, 502, 503, 504);

  private final HttpClient http;
  private final ObjectMapper json;

  public OpenAiChatClient(HttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  public static OpenAiChatClient create(ObjectMapper json) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    return new OpenAiChatClient(http, json);
  }

  @Override
  public String complete(ResolvedStage stage, String prompt, JsonNode responseFormat)
      throws LlmException {
    HttpRequest request = buildRequest(stage, prompt, responseFormat);
    long startNanos = System.nanoTime();
    HttpResponse<Stream<String>> response = awaitHeaders(stage, request);

    if (response.statusCode() != 200) {
      String detail;
      try (Stream<String> lines = response.body()) {
        detail = lines.limit(20).collect(Collectors.joining("\n"));
      } catch (UncheckedIOException e) {
        detail = "";
      }
      String message =
          "HTTP %d from %s: %s".formatted(response.statusCode(), stage.platformName(), detail);
      throw RETRYABLE_STATUS.contains(response.statusCode())
          ? LlmException.retryable(message)
          : LlmException.fatal(message);
    }
    return readStream(stage, response.body(), startNanos);
  }

  /**
   * The content, or the reasoning when content is blank. LM Studio's reasoning split can misfire
   * and put the whole reply in reasoning_content; callers' parsing (last marked block, leading
   * think block) copes with reasoning mixed into the text.
   */
  private static String answer(StringBuilder content, StringBuilder reasoning) {
    return content.toString().isBlank() && !reasoning.toString().isBlank()
        ? reasoning.toString()
        : content.toString();
  }

  private HttpRequest buildRequest(ResolvedStage stage, String prompt, JsonNode responseFormat)
      throws LlmException {
    ObjectNode body = json.createObjectNode();
    body.put("model", stage.model());
    body.put("temperature", stage.temperature());
    body.put("stream", true);
    ObjectNode message = body.putArray("messages").addObject();
    message.put("role", "user");
    message.put("content", prompt);
    if (responseFormat != null) {
      body.set("response_format", responseFormat);
    }
    String payload;
    try {
      payload = json.writeValueAsString(body);
    } catch (IOException e) {
      throw LlmException.fatal("cannot serialize request", e);
    }
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(stage.platform().base() + "/chat/completions"))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(payload));
    stage.platform().apiKey().ifPresent(k -> b.header("Authorization", "Bearer " + k));
    return b.build();
  }

  private HttpResponse<Stream<String>> awaitHeaders(ResolvedStage stage, HttpRequest request)
      throws LlmException {
    CompletableFuture<HttpResponse<Stream<String>>> future =
        http.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
    try {
      return future.get(stage.firstTokenTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw LlmException.retryable(
          "no response from %s within %ds (model busy, queued, or loading?)"
              .formatted(stage.platformName(), stage.firstTokenTimeout().toSeconds()));
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      String where = stage.platformName() + " at " + stage.platform().base();
      if (cause instanceof ConnectException) {
        throw LlmException.retryable("cannot connect to " + where, cause);
      }
      if (cause instanceof IOException) {
        throw LlmException.retryable("I/O error talking to " + where + ": " + cause, cause);
      }
      throw LlmException.fatal("request to " + where + " failed: " + cause, cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw LlmException.fatal("interrupted while waiting for " + stage.platformName());
    }
  }

  private String readStream(ResolvedStage stage, Stream<String> lines, long startNanos)
      throws LlmException {
    BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    Thread reader =
        Thread.ofVirtual()
            .name("sse-reader")
            .start(
                () -> {
                  try {
                    lines.forEach(queue::add);
                    queue.add(END);
                  } catch (RuntimeException e) {
                    queue.add(e);
                  }
                });

    StringBuilder out = new StringBuilder();
    StringBuilder reasoning = new StringBuilder();
    boolean sawData = false;
    try {
      while (true) {
        long waitMillis;
        if (sawData) {
          waitMillis = stage.idleTimeout().toMillis();
        } else {
          long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
          waitMillis = Math.max(0, stage.firstTokenTimeout().toMillis() - elapsed);
        }
        Object item = queue.poll(waitMillis, TimeUnit.MILLISECONDS);
        if (item == null) {
          throw LlmException.retryable(
              sawData
                  ? "stalled: no tokens from %s for %ds"
                      .formatted(stage.platformName(), stage.idleTimeout().toSeconds())
                  : "no tokens from %s within %ds (model busy, queued, or loading?)"
                      .formatted(stage.platformName(), stage.firstTokenTimeout().toSeconds()));
        }
        if (item == END) {
          return answer(out, reasoning);
        }
        if (item instanceof RuntimeException e) {
          throw LlmException.retryable("stream from " + stage.platformName() + " broke: " + e, e);
        }
        String line = (String) item;
        if (!SseParser.isData(line)) {
          continue;
        }
        sawData = true;
        if (SseParser.isDone(line)) {
          return answer(out, reasoning);
        }
        Optional<String> error = SseParser.error(line, json);
        if (error.isPresent()) {
          throw LlmException.fatal(stage.platformName() + " reported: " + error.get());
        }
        SseParser.content(line, json).ifPresent(out::append);
        SseParser.reasoning(line, json).ifPresent(reasoning::append);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw LlmException.fatal("interrupted while streaming from " + stage.platformName());
    } finally {
      // Closing the stream cancels the HTTP exchange, which also unblocks the reader thread.
      lines.close();
      reader.interrupt();
    }
  }
}
