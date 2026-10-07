package com.jobcrm.infrastructure.config;

/**
 * Produces a human-readable, log-safe dump of {@link JobcrmConfig} with secret fields masked. Never
 * log the raw config object — route it through {@link #redact(JobcrmConfig)} so API keys, client
 * secrets, and refresh tokens never reach a log file (development-guidelines.md § Configuration and
 * Secrets).
 *
 * <p>Secrets masked: {@code llm.api-key}, {@code gmail.client-secret}, {@code gmail.refresh-token}.
 */
public final class ConfigRedactor {

  private static final String MASK = "***";

  private ConfigRedactor() {}

  public static String redact(JobcrmConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("JobcrmConfig{\n");
    sb.append("  llm.provider=").append(config.llm().provider()).append('\n');
    sb.append("  llm.apiKey=").append(mask(config.llm().apiKey())).append('\n');
    sb.append("  llm.defaultModel=").append(config.llm().defaultModel()).append('\n');
    sb.append("  gmail.clientId=").append(config.gmail().clientId()).append('\n');
    sb.append("  gmail.clientSecret=").append(mask(config.gmail().clientSecret())).append('\n');
    sb.append("  gmail.refreshToken=").append(mask(config.gmail().refreshToken())).append('\n');
    sb.append("  gmail.pollInterval=").append(config.gmail().pollInterval()).append('\n');
    sb.append("  calendar.pollInterval=").append(config.calendar().pollInterval()).append('\n');
    sb.append("  daemon.briefingTime=").append(config.daemon().briefingTime()).append('\n');
    sb.append("  daemon.staleSweepTime=").append(config.daemon().staleSweepTime()).append('\n');
    sb.append("  daemon.silenceThreshold=").append(config.daemon().silenceThreshold()).append('\n');
    sb.append('}');
    return sb.toString();
  }

  /** Masks a secret: null stays "(unset)", present values become a fixed mask (never partial). */
  public static String mask(String secret) {
    if (secret == null || secret.isEmpty()) {
      return "(unset)";
    }
    return MASK;
  }
}
