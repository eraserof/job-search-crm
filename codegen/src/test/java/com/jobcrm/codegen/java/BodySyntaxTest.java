package com.jobcrm.codegen.java;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BodySyntaxTest {

  @Test
  void aValidBodyHasNoError() {
    assertThat(BodySyntax.firstError("int x = 1;\nreturn x;")).isEmpty();
    assertThat(BodySyntax.firstError("")).isEmpty();
  }

  @Test
  void truncatedOutputIsReported() {
    assertThat(BodySyntax.firstError("if (x) {\n  return 1;"))
        .get()
        .asString()
        .startsWith("syntax error: ");
  }

  @Test
  void proseMixedIntoTheCodeIsReported() {
    assertThat(BodySyntax.firstError("Here is the method you asked for:\nreturn 1;")).isPresent();
  }

  @Test
  void aSurplusClosingBraceIsReported() {
    assertThat(BodySyntax.firstError("return 1;\n}")).isPresent();
  }
}
