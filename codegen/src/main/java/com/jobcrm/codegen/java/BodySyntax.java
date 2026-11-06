package com.jobcrm.codegen.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.Optional;

/**
 * Cheap syntax check of a generated method body before the (slow) Gradle compile. Catches
 * truncated output, unbalanced braces, and prose mixed into the code.
 */
public final class BodySyntax {

  private BodySyntax() {}

  /**
   * The first syntax problem in {@code body}, or empty if it parses as a block.
   *
   * <ol>
   *   <li>Create {@code new JavaParser(new ParserConfiguration().setLanguageLevel(
   *       ParserConfiguration.LanguageLevel.JAVA_21))}.
   *   <li>Parse with {@code parser.parseBlock("{\n" + body + "\n}")}, which returns {@code
   *       ParseResult<BlockStmt>}.
   *   <li>If {@code result.isSuccessful()}, return {@code Optional.empty()}.
   *   <li>Otherwise return {@code Optional.of("syntax error: " + message)} where message is {@code
   *       result.getProblems().get(0).getMessage()}, or "unparsable body" when there are no
   *       problems listed.
   * </ol>
   */
  public static Optional<String> firstError(String body) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
