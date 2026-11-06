package com.jobcrm.codegen.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepairParserTest {

  private static final String SIG = "public static String clean(String raw)";
  private static final String GOOD =
      "=== REVISED PROMPT ===\nRules...\n=== Contract ===\nImplement:  public static String"
          + "   clean(String raw)\n=== END ===";

  @Test
  void extractsTheBlockBetweenTheMarkers() {
    assertThat(RepairParser.extract("Here you go:\n" + GOOD + "\nthanks", SIG))
        .contains(
            "Rules...\n=== Contract ===\nImplement:  public static String   clean(String raw)");
  }

  @Test
  void leakedReasoningThatQuotesTheMarkersIsSkippedAndDataTagsSurvive() {
    // Shape of the real cg10 reply: reasoning (no opening tag) quoting the markers, a stray
    // </think>, then the answer, whose own text contains </think> as data.
    String answer =
        "=== REVISED PROMPT ===\n"
            + "- Remove blocks with Pattern.compile(\"<think>.*?</think>\", Pattern.DOTALL)\n"
            + "=== Contract ===\nImplement:  "
            + SIG
            + "\n=== END ===";
    String raw =
        "final must be: === REVISED PROMPT === ... === END ===. Use </think> carefully.</think>\n"
            + answer;

    assertThat(RepairParser.extract(raw, SIG))
        .get()
        .asString()
        .startsWith("- Remove blocks")
        .contains("\"<think>.*?</think>\"");
  }

  @Test
  void theLastBlockWins() {
    String draft = "=== REVISED PROMPT ===\ndraft\n=== Contract ===\n" + SIG + "\n=== END ===";
    String fin = "=== REVISED PROMPT ===\nfinal\n=== Contract ===\n" + SIG + "\n=== END ===";

    assertThat(RepairParser.extract(draft + "\n" + fin, SIG)).get().asString().startsWith("final");
  }

  @Test
  void aLeadingThinkBlockIsIgnored() {
    String raw = "<think>=== REVISED PROMPT === bogus === END ===</think>\n" + GOOD;

    assertThat(RepairParser.extract(raw, SIG)).get().asString().startsWith("Rules...");
  }

  @Test
  void rejectsBlocksMissingTheContractOrTheSignature() {
    assertThat(RepairParser.extract("=== REVISED PROMPT ===\n" + SIG + "\n=== END ===", SIG))
        .isEmpty();
    assertThat(
            RepairParser.extract(
                "=== REVISED PROMPT ===\n=== Contract ===\nvoid other()\n=== END ===", SIG))
        .isEmpty();
    assertThat(RepairParser.extract("no markers at all", SIG)).isEmpty();
  }
}
