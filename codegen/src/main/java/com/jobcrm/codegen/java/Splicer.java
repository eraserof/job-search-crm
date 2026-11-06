package com.jobcrm.codegen.java;

import java.util.List;

/**
 * Replaces one method body in a source file and adds imports. Everything outside the body's
 * braces is kept byte-for-byte, including the file's line endings.
 */
public final class Splicer {

  private Splicer() {}

  /**
   * Returns {@code source} with the body of the method matching {@code signature} replaced by
   * {@code body}, then with {@code imports} added.
   *
   * @param imports fully qualified names, e.g. "java.util.UUID" or "static java.util.Objects.hash"
   * @throws SpliceException if the method is not found exactly once
   */
  public static String splice(String source, String signature, String body, List<String> imports) {
    MethodLocator.BodyRange range =
        MethodLocator.find(source, signature)
            .orElseThrow(
                () -> new SpliceException("signature not found exactly once in target: " + signature));
    String eol = source.contains("\r\n") ? "\r\n" : "\n";
    String inner = String.join(eol, trimBlankLines(body).lines().toList());
    String spliced =
        source.substring(0, range.open() + 1)
            + eol
            + (inner.isEmpty() ? "" : inner + eol)
            + range.indent()
            + source.substring(range.close());
    return addImports(spliced, imports, eol);
  }

  /**
   * Adds {@code import X;} for each entry not already present: after the last existing import, or
   * after the package declaration when there are none, or at the top as a last resort.
   */
  static String addImports(String source, List<String> imports, String eol) {
    String out = source;
    for (String imp : imports == null ? List.<String>of() : imports) {
      if (imp == null || imp.isBlank()) {
        continue;
      }
      String line = "import " + imp.strip() + ";";
      if (out.lines().anyMatch(l -> l.strip().equals(line))) {
        continue;
      }
      int at = insertionPoint(out);
      out = out.substring(0, at) + line + eol + out.substring(at);
    }
    return out;
  }

  /** Offset of the start of the line after the last import, else after the package line, else 0. */
  private static int insertionPoint(String source) {
    int lastImportEnd = -1;
    int packageEnd = -1;
    int pos = 0;
    for (String line : source.split("(?<=\n)")) {
      String s = line.strip();
      int next = pos + line.length();
      if (s.startsWith("import ")) {
        lastImportEnd = next;
      } else if (s.startsWith("package ") && packageEnd < 0) {
        packageEnd = next;
      }
      pos = next;
    }
    if (lastImportEnd >= 0) {
      return lastImportEnd;
    }
    return Math.max(packageEnd, 0);
  }

  private static String trimBlankLines(String text) {
    List<String> lines = text.lines().toList();
    int start = 0;
    int end = lines.size();
    while (start < end && lines.get(start).isBlank()) {
      start++;
    }
    while (end > start && lines.get(end - 1).isBlank()) {
      end--;
    }
    return String.join("\n", lines.subList(start, end));
  }
}
