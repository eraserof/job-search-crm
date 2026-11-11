package com.jobcrm.codegen.llm;

/**
 * Removes a thinking model's reasoning from the start of its reply.
 *
 * <p>Only a reply that STARTS with {@code <think>} is touched, and only through the first {@code
 * </think>}. Tags elsewhere are left alone on purpose: they can be data (a prompt about stripping
 * think tags contains {@code "<think>.*?</think>"}), and cutting at them corrupts the answer, which
 * is exactly what happened with a real repair reply. Reasoning that leaks without an opening tag
 * cannot be told apart from the answer here; callers handle that by taking the LAST marked block,
 * and the real fix is LM Studio's "separate reasoning_content" setting, which keeps reasoning out
 * of {@code content} entirely.
 */
public final class Reasoning {

  private static final String OPEN = "<think>";
  private static final String CLOSE = "</think>";

  private Reasoning() {}

  public static String strip(String reply) {
    if (reply == null) {
      return "";
    }
    String text = reply.stripLeading();
    if (!text.startsWith(OPEN)) {
      return reply;
    }
    int close = text.indexOf(CLOSE);
    return close < 0 ? "" : text.substring(close + CLOSE.length());
  }
}
