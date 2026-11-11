package com.jobcrm.codegen.java;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns a raw code-model reply into a method body ready for {@link Splicer}. */
public final class BodyExtractor {

  private BodyExtractor() {}

  /**
   * Removes reasoning and markdown from a reply.
   *
   * <ol>
   *   <li>Remove every {@code <think>...</think>} block, matching across lines (regex flag DOTALL,
   *       non-greedy).
   *   <li>Split into lines and drop every line whose stripped text starts with three backticks
   *       (markdown fences such as {@code ```} and {@code ```java}).
   *   <li>Join the remaining lines with "\n" and strip leading and trailing whitespace.
   * </ol>
   */
  public static String clean(String raw) {
    Pattern pattern = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(raw);
    String cleaned = matcher.replaceAll("");
    return cleaned
        .lines()
        .filter(line -> !line.strip().startsWith("```"))
        .collect(Collectors.joining("\n"))
        .strip();
  }

  /**
   * If the model echoed the whole method instead of just its body, returns only the body.
   *
   * <ol>
   *   <li>Wrap the cleaned text as a class: {@code String wrapped = "class Wrapper__ {\n" + cleaned
   *       + "\n}";}
   *   <li>{@code Optional<MethodLocator.BodyRange> range = MethodLocator.find(wrapped, signature);}
   *       (find never throws for an unparsable source; it returns empty).
   *   <li>If present, return the text strictly between the braces: {@code wrapped.substring(
   *       range.get().open() + 1, range.get().close())}, with leading and trailing blank lines
   *       removed (strip only "\n" and "\r" at the ends, not spaces, so indentation stays).
   *   <li>Otherwise return {@code cleaned} unchanged: it is already a body.
   * </ol>
   *
   * Catch IllegalArgumentException from MethodLocator (a bad signature) and return {@code cleaned}
   * in that case too.
   */
  public static String extractBody(String cleaned, String signature) {
    String wrapped = "class Wrapper__ {\n" + cleaned + "\n}";
    MethodLocator.BodyRange range;
    try {
      Optional<MethodLocator.BodyRange> optionalRange = MethodLocator.find(wrapped, signature);
      if (optionalRange.isPresent()) {
        range = optionalRange.get();
        String inner = wrapped.substring(range.open() + 1, range.close());
        return inner.replaceAll("^[\\r\\n]+", "").replaceAll("[\\r\\n]+$", "");
      }
    } catch (IllegalArgumentException e) {
      return cleaned;
    }
    return cleaned;
  }

  /** True if the reply asks a question instead of writing code (contains NEEDS_CLARIFICATION). */
  public static boolean isClarification(String cleaned) {
    return cleaned.contains("NEEDS_CLARIFICATION");
  }
}
