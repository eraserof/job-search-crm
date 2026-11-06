package com.jobcrm.codegen.job;

import com.jobcrm.codegen.llm.Reasoning;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and sanity-checks the revised job prompt from a repair-stage reply.
 *
 * <p>Hand-written: the generated version ignored its contract (it returned the whole reply) and
 * still compiled. The rules also changed after a real failure: a thinking model's reasoning quoted
 * the markers, so reasoning is stripped first and the LAST block wins.
 */
public final class RepairParser {

  private static final Pattern BLOCK =
      Pattern.compile("=== REVISED PROMPT ===\\s*(.*?)\\s*=== END ===", Pattern.DOTALL);

  private RepairParser() {}

  /**
   * The revised prompt, or empty if the reply is unusable.
   *
   * <ol>
   *   <li>Strip reasoning with {@link Reasoning#strip(String)}.
   *   <li>Take the LAST {@code === REVISED PROMPT === ... === END ===} block (the final answer
   *       comes last). None: empty.
   *   <li>The block must contain "=== Contract ===" and, with whitespace collapsed on both sides,
   *       the signature. Otherwise: empty.
   * </ol>
   */
  public static Optional<String> extract(String raw, String signature) {
    if (raw == null || signature == null) {
      return Optional.empty();
    }
    Matcher m = BLOCK.matcher(Reasoning.strip(raw));
    String last = null;
    while (m.find()) {
      last = m.group(1).strip();
    }
    if (last == null || !last.contains("=== Contract ===")) {
      return Optional.empty();
    }
    String wanted = signature.replaceAll("\\s+", " ").strip();
    if (!last.replaceAll("\\s+", " ").contains(wanted)) {
      return Optional.empty();
    }
    return Optional.of(last);
  }
}
