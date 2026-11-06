package com.jobcrm.codegen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.codegen.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanPromptsTest {

  @Test
  void plannerFillsAskAndContext() {
    assertThat(PlanPrompts.planner("ask={{ASK}} ctx={{CONTEXT}}", "do $1", "files"))
        .isEqualTo("ask=do $1 ctx=files");
  }

  @Test
  void theBundledPlannerTemplateHasBothPlaceholders() {
    String out = PlanPrompts.planner(Resources.text("planner-prompt.txt"), "THE ASK", "THE FILES");

    assertThat(out).contains("THE ASK").contains("THE FILES").doesNotContain("{{");
  }

  @Test
  void retryAppendsThePreviousPlanAndTheErrors() {
    assertThat(PlanPrompts.retry("BASE", "{}", List.of("e1", "e2")))
        .isEqualTo(
            "BASE\n\n=== Your previous plan was rejected ===\n{}"
                + "\n\n=== Validation errors ===\ne1\ne2\n\nReturn a corrected JSON plan only.");
    assertThat(PlanPrompts.retry("BASE", null, List.of("e"))).contains("rejected ===\n(none)\n");
  }

  @Test
  void rulesHeaderKeepsEverythingBeforeTheClassSection() {
    assertThat(PlanPrompts.rulesHeader("Rules:\n- a\n\n=== The class x ===\nrest"))
        .isEqualTo("Rules:\n- a");
    assertThat(PlanPrompts.rulesHeader("Rules\r\n\r\n=== The class x ===\r\n")).isEqualTo("Rules");
    assertThat(PlanPrompts.rulesHeader("no marker  \n")).isEqualTo("no marker");
    assertThat(PlanPrompts.rulesHeader("=== The class first\nrest")).isEmpty();
  }

  @Test
  void theRealTemplateYieldsOnlyTheRules() throws Exception {
    // Gradle runs codegen tests with codegen/ as the working directory.
    String template = Files.readString(Path.of("TEMPLATE.txt"));

    String rules = PlanPrompts.rulesHeader(template);

    assertThat(rules).contains("Rules:").doesNotContain("=== The class").doesNotEndWith("\n");
  }
}
