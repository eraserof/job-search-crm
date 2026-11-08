package com.jobcrm.codegen.scaffold;

import java.util.List;

/** Prompt building for the scaffold stage. The template lives in resources. */
public final class ScaffoldPrompts {

  private ScaffoldPrompts() {}

  /**
   * The scaffold prompt: {@code template} with {@code {{CODER_RULES}}}, then {@code {{ASK}}}, then
   * {@code {{CONTEXT}}} replaced by the arguments, in that order, with literal {@code
   * String.replace}. Context goes last because it is whole source files that may contain anything.
   */
  public static String build(String template, String rules, String ask, String context) {
    return template
        .replace("{{CODER_RULES}}", rules)
        .replace("{{ASK}}", ask)
        .replace("{{CONTEXT}}", context);
  }

  /** The re-ask after a rejected reply: the base prompt, the rejected reply, and the problems. */
  public static String retry(String basePrompt, String previousReply, List<String> errors) {
    return basePrompt
        + "\n\n=== Your previous reply was rejected ===\n"
        + (previousReply == null ? "(none)" : previousReply)
        + "\n\n=== Problems ===\n"
        + String.join("\n", errors)
        + "\n\nOutput the corrected blocks only, in the same format.";
  }
}
