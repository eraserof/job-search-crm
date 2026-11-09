package com.jobcrm.codegen.review;

import java.util.ArrayList;
import java.util.List;

/** Checks that a generated method body contains the literal strings required by its Javadoc contract. */
public final class ContractCheck {

  private ContractCheck() {}

  /**
   * Returns the quoted literal lines found in a Javadoc contract, in first-appearance order without
   * duplicates.
   *
   * <ol>
   *   <li>If {@code javadoc} is null, throw new IllegalArgumentException("javadoc must not be null").
   *   <li>Create {@code ArrayList<String> out = new ArrayList<>();}.
   *   <li>For each {@code String raw} in {@code javadoc.split("\\R")}: let {@code String line =
   *       raw.trim();}; while {@code line.startsWith("*")}, set {@code line = line.substring(1).trim();}. Then
   *       decode HTML entities with {@code line = line.replace("&lt;", "<").replace("&gt;", ">").replace("&#64;",
   *       "@").replace("&amp;", "&");}. If
   *       {@code line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"") && !out.contains(line)}, call
   *       {@code out.add(line);}.
   *   <li>Return {@code out}.
   * </ol>
   *
   * <p>A literal is a line that, after trimming whitespace, removing leading asterisks from Javadoc comment
   * formatting, and decoding HTML entities, begins with a double quote character and ends with a double quote
   * character. The returned literals are the decoded lines and include their surrounding double quotes. If
   * {@code javadoc} is empty or has no such lines, return an empty list.
   *
   * <p>Entities are decoded because Javadoc requires {@code &lt;}, {@code &gt;}, {@code &amp;} and {@code &#64;} in
   * place of the characters {@code <}, {@code >}, {@code &} and {@code @}, while the method body contains the plain
   * characters. {@code &amp;} is replaced last, so that the text {@code &amp;lt;} becomes {@code &lt;} and is not
   * decoded a second time.
   *
   * <pre>
   * "javadoc must not be null"
   * "\\R"
   * "*"
   * "\""
   * </pre>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code String[] String.split(String regex)}
   *   <li>{@code String String.trim()}
   *   <li>{@code boolean String.startsWith(String prefix)}
   *   <li>{@code String String.substring(int beginIndex)}
   *   <li>{@code int String.length()}
   *   <li>{@code boolean String.endsWith(String suffix)}
   *   <li>{@code String String.replace(CharSequence target, CharSequence replacement)}
   *   <li>{@code ArrayList()}
   *   <li>{@code boolean List.contains(Object o)}
   *   <li>{@code void List.add(E e)}
   *   <li>{@code IllegalArgumentException(String message)}
   * </ul>
   */
  public static List<String> literals(String javadoc) {
    if (javadoc == null) {
        throw new IllegalArgumentException("javadoc must not be null");
    }
    List<String> out = new ArrayList<>();
    for (String raw : javadoc.split("\\R")) {
        String line = raw.trim();
        while (line.startsWith("*")) {
            line = line.substring(1).trim();
        }
        line = line.replace("&lt;", "<").replace("&gt;", ">").replace("&#64;", "@").replace("&amp;", "&");
        if (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"") && !out.contains(line)) {
            out.add(line);
        }
    }
    return out;
  }

  /**
   * Returns the required literals from a Javadoc contract that are absent from a method body.
   *
   * <ol>
   *   <li>If {@code javadoc} is null, throw new IllegalArgumentException("javadoc must not be null").
   *   <li>If {@code body} is null, throw new IllegalArgumentException("body must not be null").
   *   <li>Let {@code List<String> required = literals(javadoc);}.
   *   <li>Create {@code ArrayList<String> out = new ArrayList<>();}.
   *   <li>For each {@code String literal} in {@code required}: if {@code !body.contains(literal)}, call
   *       {@code out.add(literal);}.
   *   <li>Return {@code out}.
   * </ol>
   *
   * <p>The match is a case-sensitive exact substring test. The result preserves the order of {@code required} and
   * contains no duplicates because {@code literals(javadoc)} removes duplicates. If there are no required literals,
   * return an empty list.
   *
   * <pre>
   * "javadoc must not be null"
   * "body must not be null"
   * </pre>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean String.contains(CharSequence s)}
   *   <li>{@code ArrayList()}
   *   <li>{@code void List.add(E e)}
   *   <li>{@code IllegalArgumentException(String message)}
   * </ul>
   */
  public static List<String> missing(String javadoc, String body) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
