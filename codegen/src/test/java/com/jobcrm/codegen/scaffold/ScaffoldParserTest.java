package com.jobcrm.codegen.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScaffoldParserTest {

  @Test
  void parsesFileAddToAndManualBlocks() {
    String reply =
        """
        chatter before
        === FILE: a/B.java ===
        package a;
        final class B {}
        === END FILE ===
        === ADD TO: a/C.java :: C ===
        void f() {}
        === END ADD ===
        === MANUAL ===
        - register B
        - rename C
        === END MANUAL ===
        """;

    ScaffoldReply parsed = ScaffoldParser.parse(reply);

    assertThat(parsed.files()).hasSize(1);
    assertThat(parsed.files().get(0).path()).isEqualTo("a/B.java");
    assertThat(parsed.files().get(0).source()).isEqualTo("package a;\nfinal class B {}\n");
    assertThat(parsed.additions().get(0).className()).isEqualTo("C");
    assertThat(parsed.manual()).containsExactly("register B", "rename C");
    assertThat(parsed.problems()).isEmpty();
  }

  @Test
  void unclosedBlockIsAProblemNotAFile() {
    ScaffoldReply parsed = ScaffoldParser.parse("=== FILE: a/B.java ===\npackage a;\n");

    assertThat(parsed.files()).isEmpty();
    assertThat(parsed.problems()).hasSize(1);
  }

  @Test
  void nullAndEmptyReplyGiveEmptyResult() {
    assertThat(ScaffoldParser.parse(null).files()).isEmpty();
    assertThat(ScaffoldParser.parse("").problems()).isEmpty();
  }
}
