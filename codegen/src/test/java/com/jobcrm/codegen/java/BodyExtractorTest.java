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
  void clarificationIsDetected() {
    assertThat(BodyExtractor.isClarification("NEEDS_CLARIFICATION: which id?")).isTrue();
    assertThat(BodyExtractor.isClarification("return 1;")).isFalse();
  }
}
