package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.config.Platform;
import com.jobcrm.codegen.config.ResolvedStage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Streams from a local fake server to exercise both timeouts and error mapping. */
class OpenAiChatClientTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AtomicReference<String> lastRequest = new AtomicReference<>();
  private HttpServer server;
  private ExecutorService pool;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
    if (pool != null) {
      pool.shutdownNow();
    }
  }

  @Test
  void concatenatesStreamedContentAndIgnoresReasoning() throws Exception {
    start(
        ex -> {
          stream(ex);
          send(ex, ": keep-alive\n\n");
          send(ex, event("{\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}"));
          send(ex, event("{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}"));
          send(ex, event("{\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}"));
          send(ex, "data: [DONE]\n\n");
          ex.close();
        });

    String text = client().complete(stage(Duration.ofSeconds(5), Duration.ofSeconds(5)), "hi", null);

    assertThat(text).isEqualTo("Hello");
    assertThat(lastRequest.get()).contains("\"stream\":true").contains("\"content\":\"hi\"");
  }

  @Test
  void aStallAfterDataStartedIsRetryable() throws Exception {
    start(
        ex -> {
          stream(ex);
          send(ex, event("{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}"));
          sleep(5_000);
          ex.close();
        });

    assertThatThrownBy(
            () -> client().complete(stage(Duration.ofSeconds(5), Duration.ofMillis(300)), "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
        .hasMessageContaining("stalled");
  }

  @Test
  void noResponseWithinTheFirstTokenTimeoutIsRetryable() throws Exception {
    start(
        ex -> {
          sleep(5_000);
          ex.close();
        });

    assertThatThrownBy(
            () -> client().complete(stage(Duration.ofMillis(300), Duration.ofSeconds(5)), "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
        .hasMessageContaining("busy");
  }

  @Test
  void headersWithoutDataStillCountAgainstTheFirstTokenTimeout() throws Exception {
    // LM Studio can accept a request and then queue it: headers arrive, tokens do not.
    start(
        ex -> {
          stream(ex);
          for (int i = 0; i < 50; i++) {
            send(ex, "\n");
            sleep(100);
          }
          ex.close();
        });

    assertThatThrownBy(
            () -> client().complete(stage(Duration.ofMillis(500), Duration.ofSeconds(5)), "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
        .hasMessageContaining("no tokens");
  }

  @Test
  void busyServerIsRetryableAndBadRequestIsFatal() throws Exception {
    AtomicReference<Integer> status = new AtomicReference<>(503);
    start(
        ex -> {
          byte[] body = "{\"error\":\"nope\"}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(status.get(), body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    ResolvedStage stage = stage(Duration.ofSeconds(5), Duration.ofSeconds(5));

    assertThatThrownBy(() -> client().complete(stage, "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
        .hasMessageContaining("HTTP 503");

    status.set(400);
    assertThatThrownBy(() -> client().complete(stage, "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
        .hasMessageContaining("HTTP 400");
  }

  @Test
  void anErrorEventIsFatal() throws Exception {
    start(
        ex -> {
          stream(ex);
          send(ex, event("{\"error\":{\"message\":\"model crashed\"}}"));
          ex.close();
        });

    assertThatThrownBy(
            () -> client().complete(stage(Duration.ofSeconds(5), Duration.ofSeconds(5)), "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
        .hasMessageContaining("model crashed");
  }

  @Test
  void anUnreachableServerIsRetryable() {
    ResolvedStage stage =
        new ResolvedStage(
            "code",
            "nowhere",
            new Platform("ollama", "http://127.0.0.1:1/v1", null),
            "m",
            0.2,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5));

    assertThatThrownBy(() -> client().complete(stage, "hi", null))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue());
  }

  // ---------- helpers ----------

  private OpenAiChatClient client() {
    return OpenAiChatClient.create(json);
  }

  private ResolvedStage stage(Duration firstToken, Duration idle) {
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    return new ResolvedStage("code", "fake", new Platform("ollama", base, null), "m", 0.2, firstToken, idle);
  }

  private void start(HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    pool =
        Executors.newCachedThreadPool(
            r -> {
              Thread t = new Thread(r);
              t.setDaemon(true);
              return t;
            });
    server.setExecutor(pool);
    server.createContext(
        "/v1/chat/completions",
        ex -> {
          lastRequest.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            handler.handle(ex);
          } catch (IOException e) {
            // the client hung up on purpose (timeout tests)
          }
        });
    server.start();
  }

  private static void stream(HttpExchange ex) throws IOException {
    ex.getResponseHeaders().add("Content-Type", "text/event-stream");
    ex.sendResponseHeaders(200, 0); // 0 = chunked, so lines are flushed as they are written
  }

  private static void send(HttpExchange ex, String text) throws IOException {
    OutputStream out = ex.getResponseBody();
    out.write(text.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static String event(String jsonPayload) {
    return "data: " + jsonPayload + "\n\n";
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
