package com.jobcrm.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.config.Platform;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Asks a platform which models it has: {@code GET <baseUrl>/models}. */
public final class ModelLister {

  private final HttpClient http;
  private final ObjectMapper json;

  public ModelLister(HttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  /**
   * Returns the {@code id} of every entry in the response's {@code data} array, in order.
   *
   * <ol>
   *   <li>Build a GET request to {@code URI.create(platform.base() + "/models")} with a 10 second
   *       request timeout; add {@code Authorization: Bearer <key>} when {@code platform.apiKey()}
   *       is present.
   *   <li>Send it with {@code http.send(request, HttpResponse.BodyHandlers.ofString())}.
   *   <li>IOException: throw {@code LlmException.retryable("cannot reach " + platform.base() + ": "
   *       + e.getMessage(), e)}. InterruptedException: re-set the interrupt flag and throw {@code
   *       LlmException.fatal("interrupted", e)}.
   *   <li>Status other than 200: throw {@code LlmException.fatal("HTTP " + status + " from " +
   *       platform.base() + "/models")}.
   *   <li>Parse the body with {@code json.readTree}; for each element of {@code path("data")} add
   *       {@code path("id").asText()} to a new ArrayList, skipping blank ids. Return the list.
   * </ol>
   */
  public List<String> list(Platform platform) throws LlmException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(platform.base() + "/models"))
            .timeout(Duration.ofSeconds(10))
            .GET();
    platform.apiKey().ifPresent(k -> builder.header("Authorization", "Bearer " + k));

    List<String> models = new ArrayList<>();
    try {
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw LlmException.fatal("HTTP " + response.statusCode() + " from " + platform.base() + "/models");
        JsonNode root = json.readTree(response.body());
        for (JsonNode node : root.path("data")) {
            String id = node.path("id").asText();
            if (!id.isBlank()) models.add(id);
        }
    } catch (IOException e) {
        throw LlmException.retryable("cannot reach " + platform.base() + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw LlmException.fatal("interrupted", e);
    }

    return models;
  }

  /**
   * True if {@code model} is in {@code available}, also accepting Ollama's implicit tag ({@code
   * "coder"} matches {@code "coder:latest"}).
   */
  public static boolean contains(List<String> available, String model) {
    if (available == null || model == null) {
        return false;
    }
    return available.contains(model) || available.contains(model + ":latest");
  }
}
