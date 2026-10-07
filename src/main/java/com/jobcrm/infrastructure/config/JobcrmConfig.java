package com.jobcrm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed, immutable view of the user-facing configuration (see ui-cli.md § Configuration File).
 * Bound by Spring Boot from, in increasing precedence: the packaged {@code application.yml}, the
 * optional {@code ~/.jobcrm/config.yml} overlay, and {@code JOBCRM_*} environment variables
 * (relaxed binding maps e.g. {@code JOBCRM_LLM_API_KEY} → {@code jobcrm.llm.api-key}).
 *
 * <p>Records, not mutable POJOs, per development-guidelines.md. Secret fields are redacted by
 * {@link ConfigRedactor} before any logging.
 */
@ConfigurationProperties(prefix = "jobcrm")
public record JobcrmConfig(Llm llm, Gmail gmail, Calendar calendar, Daemon daemon) {

  public JobcrmConfig {
    if (llm == null) {
      llm = new Llm(null, null, null);
    }
    if (gmail == null) {
      gmail = new Gmail(null, null, null, "5m");
    }
    if (calendar == null) {
      calendar = new Calendar("5m");
    }
    if (daemon == null) {
      daemon = new Daemon("07:00", "03:00", "14d");
    }
  }

  /** LLM provider settings. {@code apiKey} is secret. */
  public record Llm(
      String provider, String apiKey, @DefaultValue("claude-sonnet-4-5") String defaultModel) {}

  /** Gmail OAuth settings. {@code clientSecret} and {@code refreshToken} are secret. */
  public record Gmail(
      String clientId,
      String clientSecret,
      String refreshToken,
      @DefaultValue("5m") String pollInterval) {}

  /** Google Calendar settings. */
  public record Calendar(@DefaultValue("5m") String pollInterval) {}

  /** Daemon scheduling settings. */
  public record Daemon(
      @DefaultValue("07:00") String briefingTime,
      @DefaultValue("03:00") String staleSweepTime,
      @DefaultValue("14d") String silenceThreshold) {}
}
