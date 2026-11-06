package com.jobcrm.codegen.config;

import java.util.Optional;

/**
 * One model server from {@code models.json} "platforms", e.g. {@code {"kind": "lmstudio",
 * "baseUrl": "http://192.168.0.49:1234/v1", "apiKeyEnv": "LMSTUDIO_API_TOKEN"}}.
 *
 * @param kind "ollama" or "lmstudio" (only lmstudio gets an enforced JSON response_format)
 * @param baseUrl OpenAI-compatible base URL, ending in /v1
 * @param apiKeyEnv nullable name of an environment variable holding a bearer token
 */
public record Platform(String kind, String baseUrl, String apiKeyEnv) {

  /** The bearer token from {@link #apiKeyEnv()}, if that variable is set and non-blank. */
  public Optional<String> apiKey() {
    if (apiKeyEnv == null || apiKeyEnv.isBlank()) {
      return Optional.empty();
    }
    String value = System.getenv(apiKeyEnv);
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  /** {@link #baseUrl()} without a trailing slash, so paths can be appended. */
  public String base() {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public boolean isLmStudio() {
    return "lmstudio".equalsIgnoreCase(kind);
  }
}
