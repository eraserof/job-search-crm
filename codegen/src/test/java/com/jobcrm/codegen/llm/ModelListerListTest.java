package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.config.Platform;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModelListerListTest {

  private final AtomicReference<String> authorization = new AtomicReference<>("(not called)");
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void returnsTheIdsInOrderAndSkipsBlankOnes() throws Exception {
    start(200, "{\"data\":[{\"id\":\"a\"},{\"id\":\"\"},{\"id\":\"b:latest\"},{}]}");

    assertThat(lister().list(platform())).containsExactly("a", "b:latest");
    assertThat(authorization.get()).isNull(); // no key configured, so no Authorization header
  }

  @Test
  void anEmptyOrMissingDataArrayGivesAnEmptyList() throws Exception {
    start(200, "{}");

    assertThat(lister().list(platform())).isEmpty();
  }

  @Test
  void aNon200ResponseIsFatal() throws Exception {
    start(401, "{\"error\":\"nope\"}");

    assertThatThrownBy(() -> lister().list(platform()))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
        .hasMessageContaining("HTTP 401");
  }

  @Test
  void anUnreachableServerIsRetryable() {
    Platform dead = new Platform("ollama", "http://127.0.0.1:1/v1", null);

    assertThatThrownBy(() -> lister().list(dead))
        .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
        .hasMessageContaining("cannot reach");
  }

  private ModelLister lister() {
    return new ModelLister(HttpClient.newHttpClient(), new ObjectMapper());
  }

  private Platform platform() {
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    return new Platform("ollama", base, "CODEGEN_TEST_SURELY_UNSET_VARIABLE");
  }

  private void start(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/models",
        ex -> {
          authorization.set(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(status, bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
  }
}
