package com.jobcrm.codegen.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds a method's body in Java source by signature, using JavaParser rather than brace matching.
 *
 * <p>A signature such as {@code "public List<Opportunity> findByContact(ContactId contact)"}
 * matches by <b>method name and parameter types</b> only. Modifiers, annotations, return type,
 * and parameter names are ignored, so overloads are told apart and cosmetic differences are not.
 * Package qualifiers are ignored too ({@code java.util.List} equals {@code List}). Only methods
 * with a body count, so interface declarations never match. Constructors are not supported.
 */
public final class MethodLocator {

  /** Lowercase package segments before a type name, e.g. "java.util." in "java.util.List". */
  private static final Pattern QUALIFIER = Pattern.compile("\\b(?:[a-z_$][\\w$]*\\.)+(?=[A-Z_$])");

  private MethodLocator() {}

  /**
   * Character offsets of a method body inside the source text.
   *
   * @param open offset of the body's opening {@code '{'}
   * @param close offset of the body's closing {@code '}'}
   * @param indent the whitespace that starts the line holding the method name
   */
  public record BodyRange(int open, int close, String indent) {}

  /** The body of the one method matching {@code signature}; empty if none or more than one. */
  public static Optional<BodyRange> find(String source, String signature) {
    List<MethodDeclaration> matches = matches(source, signature);
    if (matches == null || matches.size() != 1) {
      return Optional.empty();
    }
    return toRange(source, matches.get(0));
  }

  /**
   * How many methods with a body match {@code signature}; -1 if {@code source} does not parse.
   *
   * @throws IllegalArgumentException if {@code signature} is not a method signature
   */
  public static int count(String source, String signature) {
    List<MethodDeclaration> matches = matches(source, signature);
    return matches == null ? -1 : matches.size();
  }

  private static List<MethodDeclaration> matches(String source, String signature) {
    MethodKey wanted = keyOf(signature);
    ParseResult<CompilationUnit> parsed = parser().parse(source);
    if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
      return null;
    }
    List<MethodDeclaration> out = new ArrayList<>();
    for (MethodDeclaration m : parsed.getResult().get().findAll(MethodDeclaration.class)) {
      if (m.getBody().isPresent() && keyOf(m).equals(wanted)) {
        out.add(m);
      }
    }
    return out;
  }

  private static Optional<BodyRange> toRange(String source, MethodDeclaration method) {
    BlockStmt body = method.getBody().orElseThrow();
    Optional<Range> range = body.getRange();
    Optional<Position> nameAt = method.getName().getBegin();
    if (range.isEmpty() || nameAt.isEmpty()) {
      return Optional.empty();
    }
    int[] lineStarts = lineStarts(source);
    int open = offset(lineStarts, range.get().begin);
    int close = offset(lineStarts, range.get().end);
    // Defensive: if positions and text ever disagree (tabs, odd line endings), refuse to splice.
    if (open < 0
        || close >= source.length()
        || source.charAt(open) != '{'
        || source.charAt(close) != '}') {
      return Optional.empty();
    }
    int lineStart = lineStarts[nameAt.get().line - 1];
    int i = lineStart;
    while (i < source.length() && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) {
      i++;
    }
    return Optional.of(new BodyRange(open, close, source.substring(lineStart, i)));
  }

  /** Offsets where each line starts; "\r\n", "\n", and "\r" each end a line, as in JavaParser. */
  private static int[] lineStarts(String s) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\r') {
        if (i + 1 < s.length() && s.charAt(i + 1) == '\n') {
          i++;
        }
        starts.add(i + 1);
      } else if (c == '\n') {
        starts.add(i + 1);
      }
    }
    return starts.stream().mapToInt(Integer::intValue).toArray();
  }

  private static int offset(int[] lineStarts, Position p) {
    if (p.line < 1 || p.line > lineStarts.length) {
      return -1;
    }
    return lineStarts[p.line - 1] + p.column - 1;
  }

  private record MethodKey(String name, List<String> paramTypes) {}

  private static MethodKey keyOf(String signature) {
    ParseResult<MethodDeclaration> parsed = parser().parseMethodDeclaration(signature.strip() + " {}");
    if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
      throw new IllegalArgumentException("not a method signature: " + signature);
    }
    return keyOf(parsed.getResult().get());
  }

  private static MethodKey keyOf(MethodDeclaration m) {
    List<String> types = new ArrayList<>();
    for (Parameter p : m.getParameters()) {
      types.add(normalize(p.getType().asString()) + (p.isVarArgs() ? "..." : ""));
    }
    return new MethodKey(m.getNameAsString(), types);
  }

  private static String normalize(String type) {
    return QUALIFIER.matcher(type.replaceAll("\\s+", "")).replaceAll("");
  }

  private static JavaParser parser() {
    ParserConfiguration config =
        new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setTabSize(1);
    return new JavaParser(config);
  }
}
