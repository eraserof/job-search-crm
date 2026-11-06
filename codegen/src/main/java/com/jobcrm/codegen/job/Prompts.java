package com.jobcrm.codegen.job;

/** Builds the retry and repair prompts. Templates live in resources; see {@code repair-prompt.txt}. */
public final class Prompts {

  private Prompts() {}

  /**
   * The retry prompt after a failed attempt: the job prompt plus the errors as a warning. It must
   * NOT include the failed code, because the small model tends to copy back what it is shown.
   * Returns exactly:
   *
   * <pre>
   * jobPrompt + "\n\n=== Warning: an earlier attempt at this method failed to compile ===\n"
   *     + errors
   *     + "\n\nWrite the method so these errors cannot happen. Output ONLY the method implementation."
   * </pre>
   */
  public static String hint(String jobPrompt, String errors) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * What went wrong, for the repair prompt. If {@code last.clarification()}: {@code "The coder
   * asked for clarification instead of writing code:\n" + last.errors()}. Otherwise: {@code "Its
   * last attempt:\n" + last.body() + "\n\nCompiler errors:\n" + last.errors()}.
   */
  public static String problem(Attempt last) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The repair prompt: {@code template} with {@code "{{JOB_PROMPT}}"} replaced by {@code
   * jobPrompt} and {@code "{{PROBLEM}}"} replaced by {@link #problem(Attempt)}. Use {@code
   * String.replace} (literal, not regex), replacing JOB_PROMPT first.
   */
  public static String repair(String template, String jobPrompt, Attempt last) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
