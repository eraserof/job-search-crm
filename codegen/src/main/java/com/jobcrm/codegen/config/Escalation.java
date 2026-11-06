package com.jobcrm.codegen.config;

/**
 * The "escalation" block of {@code models.json}. Boxed fields may be null in the JSON.
 *
 * @param codeAttempts nullable; code-stage attempts per round; default 2, minimum 1
 * @param repairRounds nullable; repair-stage rounds after the first round fails; default 0
 * @param repairMode nullable; only "prompt" is supported; default "prompt"
 */
public record Escalation(Integer codeAttempts, Integer repairRounds, String repairMode) {

  /**
   * Returns a copy with every null replaced by its default: codeAttempts 2 (and any value below 1
   * becomes 1), repairRounds 0 (any negative value becomes 0), repairMode "prompt".
   */
  public Escalation withDefaults() {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
