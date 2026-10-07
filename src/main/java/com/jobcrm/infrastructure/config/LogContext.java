package com.jobcrm.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Safe push/pop of SLF4J {@link MDC} keys. Use with try-with-resources so keys are always removed,
 * even on exception, and so a thread never leaks context into unrelated work:
 *
 * <pre>{@code
 * try (var ignored = LogContext.with("agentRunId", runId).and("agentName", name)) {
 *   // log lines here carry agentRunId + agentName
 * }
 * }</pre>
 *
 * <p>Recognised keys (development-guidelines.md § Logging Conventions): {@code agentRunId}, {@code
 * agentName}, {@code opportunityId}, {@code interactionId}. Any key is accepted; these are the
 * conventional ones the log pattern surfaces.
 */
public final class LogContext implements AutoCloseable {

  private final Map<String, String> previous = new LinkedHashMap<>();

  private LogContext() {}

  /** Opens a context with one key set. */
  public static LogContext with(String key, String value) {
    return new LogContext().and(key, value);
  }

  /** Sets an additional key, remembering any prior value so it can be restored on close. */
  public LogContext and(String key, String value) {
    previous.put(key, MDC.get(key));
    if (value == null) {
      MDC.remove(key);
    } else {
      MDC.put(key, value);
    }
    return this;
  }

  /**
   * Restores every key this context touched to its prior value (or removes it if there was none).
   */
  @Override
  public void close() {
    for (Map.Entry<String, String> e : previous.entrySet()) {
      if (e.getValue() == null) {
        MDC.remove(e.getKey());
      } else {
        MDC.put(e.getKey(), e.getValue());
      }
    }
    previous.clear();
  }
}
