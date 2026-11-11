package com.jobcrm.codegen.java;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MethodLocatorJavadocTest {

  private static final String SOURCE =
      """
      class A {
        /**
         * Returns the answer.
         *
         * <pre>
         * "forty-two"
         * </pre>
         */
        int answer() {
          return 42;
        }

        /** Overload. */
        int answer(int n) {
          return n;
        }

        int bare() {
          return 0;
        }

        /** An unrelated comment on the field below, not on a method. */
        int field;
      }
      """;

  @Test
  void returnsTheRawJavadocTextWithItsLeadingAsterisks() {
    String javadoc = MethodLocator.javadoc(SOURCE, "int answer()").orElseThrow();

    assertThat(javadoc).contains("Returns the answer.").contains("* \"forty-two\"");
    assertThat(javadoc).doesNotContain("Overload").doesNotContain("/**");
  }

  @Test
  void picksTheOverloadByParameterTypes() {
    assertThat(MethodLocator.javadoc(SOURCE, "int answer(int n)"))
        .hasValueSatisfying(javadoc -> assertThat(javadoc).contains("Overload."));
  }

  @Test
  void isEmptyWithoutJavadocOrWithoutAMatchOrWhenTheSourceDoesNotParse() {
    assertThat(MethodLocator.javadoc(SOURCE, "int bare()")).isEmpty();
    assertThat(MethodLocator.javadoc(SOURCE, "int missing()")).isEmpty();
    assertThat(MethodLocator.javadoc("class {", "int answer()")).isEmpty();
  }
}
