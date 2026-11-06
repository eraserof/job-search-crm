package com.jobcrm.codegen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;

/**
 * The parsed {@code codegen/models.json}: named platforms (servers), named stages (platform +
 * model + settings), and the escalation ladder. {@code escalation} may be null.
 */
public record ModelsConfig(
    Map<String, Platform> platforms, Map<String, StageSpec> stages, Escalation escalation) {

  /**
   * Reads {@code file} with {@code json} (an ObjectMapper configured to ignore unknown
   * properties). Throws {@link ConfigException} with the file path in the message if the file is
   * missing or is not valid JSON. A null "platforms" or "stages" becomes an empty map.
   */
  public static ModelsConfig load(Path file, ObjectMapper json) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * Looks up stage {@code stageName} and its platform and returns a {@link ResolvedStage}:
   * temperature defaults to 0.2, firstTokenTimeout to 900 seconds, idleTimeout to 120 seconds.
   * Throws {@link ConfigException} if the stage is missing ("stage 'x' not in models.json") or
   * its platform is missing ("stage 'x' uses unknown platform 'y'").
   */
  public ResolvedStage resolve(String stageName) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /** True if a stage with this name exists. */
  public boolean hasStage(String stageName) {
    return stages != null && stages.containsKey(stageName);
  }

  /** The escalation block with defaults applied; an absent block is all defaults. */
  public Escalation effectiveEscalation() {
    Escalation e = escalation == null ? new Escalation(null, null, null) : escalation;
    return e.withDefaults();
  }
}
