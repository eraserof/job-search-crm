package com.jobcrm.codegen.job;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts and sanity-checks the revised job prompt from a repair-stage reply. */
public final class RepairParser {

  private RepairParser() {}

  /**
   * The revised prompt, or empty if the reply is unusable.
   *
   * <ol>
   *   <li>Remove every {@code <think>...</think>} block (DOTALL, non-greedy).
   *   <li>Find {@code Pattern.compile("=== REVISED PROMPT ===\\s*(.*?)\\s*=== END ===",
   *       Pattern.DOTALL)}. No match: return empty.
   *   <li>Take group 1, stripped. It must contain "=== Contract ===", else return empty.
   *   <li>It must still contain the signature: collapse every run of whitespace to one space in
   *       both the revised text and {@code signature} ({@code replaceAll("\\s+", " ")}, then
   *       {@code strip()} the signature) and check {@code contains}. Missing: return empty.
   *   <li>Return the stripped group 1 (not the collapsed copy).
   * </ol>
   */
  public static Optional<String> extract(String raw, String signature) {
    if (raw == null || signature == null) {
        return Optional.empty();
    }
    String cleaned = Pattern.compile("</think>", Pattern.DOTALL).matcher(raw).replaceAll("");
    return Optional.of(cleaned);
  }
}
