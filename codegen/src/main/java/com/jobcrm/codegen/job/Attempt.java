package com.jobcrm.codegen.job;

/**
 * The result of one code-stage attempt.
 *
 * @param ok it compiled (or compiling was skipped)
 * @param clarification the model asked a question (NEEDS_CLARIFICATION) instead of writing code
 * @param body the extracted body, or the question when {@code clarification}
 * @param errors compiler or syntax errors for the target file; empty when ok
 */
public record Attempt(boolean ok, boolean clarification, String body, String errors) {

  static Attempt success(String body) {
    return new Attempt(true, false, body, "");
  }

  static Attempt failed(String body, String errors) {
    return new Attempt(false, false, body, errors);
  }

  static Attempt question(String text) {
    return new Attempt(false, true, text, text);
  }
}
