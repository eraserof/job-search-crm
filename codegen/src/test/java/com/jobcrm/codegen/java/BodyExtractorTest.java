package com.jobcrm.codegen.java;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BodyExtractorTest {

  @Test
  void cleanRemovesThinkBlocksAndFences() {
    String raw = "<think>\nlet me see\n</think>\n```java\nreturn 42;\n```\n";

    assertThat(BodyExtractor.clean(raw)).isEqualTo("return 42;");
  }

  @Test
  void cleanKeepsIndentationInsideTheBody() {
    String raw = "```\nif (x) {\n  return 1;\n}\n```";

    assertThat(BodyExtractor.clean(raw)).isEqualTo("if (x) {\n  return 1;\n}");
  }

  @Test
  void extractBodyUnwrapsAnEchoedMethodAndKeepsIndentation() {
    String echoed = "public int answer() {\n    int x = 40;\n    return x + 2;\n}";

    assertThat(BodyExtractor.extractBody(echoed, "int answer()"))
        .isEqualTo("    int x = 40;\n    return x + 2;");
  }

  @Test
  void extractBodyLeavesAPlainBodyAlone() {
    assertThat(BodyExtractor.extractBody("return 42;", "int answer()")).isEqualTo("return 42;");
    assertThat(BodyExtractor.extractBody("if (a) {\n  return 1;\n}\nreturn 2;", "int answer()"))
        .isEqualTo("if (a) {\n  return 1;\n}\nreturn 2;");
  }

  @Test
  void extractBodyIgnoresAMethodWithADifferentSignature() {
    String other = "int other() {\n  return 1;\n}";

    assertThat(BodyExtractor.extractBody(other, "int answer()")).isEqualTo(other);
  }

  @Test
  void extractBodyTreatsABadSignatureAsAPlainBody() {
    assertThat(BodyExtractor.extractBody("return 1;", "not a signature")).isEqualTo("return 1;");
  }

  @Test
  void clarificationIsDetected() {
    assertThat(BodyExtractor.isClarification("NEEDS_CLARIFICATION: which id?")).isTrue();
    assertThat(BodyExtractor.isClarification("return 1;")).isFalse();
  }
}
