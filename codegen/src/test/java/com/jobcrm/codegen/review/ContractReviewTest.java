package com.jobcrm.codegen.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractReviewTest {

  private static String source(String body) {
    return """
        class A {
          /**
           * Returns the answer.
           *
           * <pre>
           * "forty-two"
           * "cannot answer"
           * </pre>
           */
          int answer() {
        %s
          }
        }
        """
        .formatted(body);
  }

  private static final String SIGNATURE = "int answer()";

  @Test
  void aBodyWithEveryLiteralHasNothingMissing() {
    String src =
        source(
            "    String a = \"forty-two\";\n    throw new IllegalStateException(\"cannot answer\");");

    ContractReview.Result result = ContractReview.check(src, SIGNATURE);

    assertThat(result.checked()).isEqualTo(2);
    assertThat(result.missing()).isEmpty();
  }

  @Test
  void aMissingLiteralIsReportedInContractOrder() {
    ContractReview.Result result = ContractReview.check(source("    return 42;"), SIGNATURE);

    assertThat(result.checked()).isEqualTo(2);
    assertThat(result.missing()).containsExactly("\"forty-two\"", "\"cannot answer\"");
  }

  @Test
  void onlyTheBodyCountsNotTheJavadocAboveIt() {
    // the literals are in the Javadoc itself; a body without them must still be flagged
    ContractReview.Result result = ContractReview.check(source("    return 1;"), SIGNATURE);

    assertThat(result.missing()).hasSize(2);
  }

  @Test
  void aMethodWithoutJavadocOrNotFoundChecksNothing() {
    String bare = "class A { int answer() { return 1; } }";

    assertThat(ContractReview.check(bare, SIGNATURE))
        .isEqualTo(new ContractReview.Result(0, java.util.List.of()));
    assertThat(ContractReview.check(source("    return 1;"), "int nope()").checked()).isZero();
  }

  @Test
  void theReportNamesTheJobAndListsEachMissingLiteral() {
    ContractReview.Result result =
        new ContractReview.Result(3, java.util.List.of("\"x\"", "\"y\""));

    assertThat(ContractReview.report("A.answer", result))
        .isEqualTo(
            "review of A.answer: 2 of 3 contract literal(s) not found in the body\n"
                + "  WARN \"x\"\n"
                + "  WARN \"y\"\n");
  }
}
