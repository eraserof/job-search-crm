package com.jobcrm.codegen.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.codegen.Resources;
import org.junit.jupiter.api.Test;

class PromptsTest {

  @Test
  void hintAddsTheErrorsButNotTheFailedCode() {
    assertThat(Prompts.hint("JOB", "ERR"))
        .isEqualTo(
            "JOB\n\n=== Warning: an earlier attempt at this method failed to compile ===\nERR"
                + "\n\nWrite the method so these errors cannot happen. Output ONLY the method"
                + " implementation.");
  }

  @Test
  void problemDescribesACompileFailureOrAQuestion() {
    assertThat(Prompts.problem(Attempt.failed("BODY", "ERR")))
        .isEqualTo("Its last attempt:\nBODY\n\nCompiler errors:\nERR");
    assertThat(Prompts.problem(Attempt.question("NEEDS_CLARIFICATION: which id?")))
        .isEqualTo(
            "The coder asked for clarification instead of writing code:\n"
                + "NEEDS_CLARIFICATION: which id?");
  }

  @Test
  void repairFillsBothPlaceholdersLiterally() {
    String out =
        Prompts.repair(
            "A {{JOB_PROMPT}} B {{PROBLEM}}", "has $1 and {{PROBLEM}}", Attempt.failed("X", "Y"));

    // JOB_PROMPT is replaced first, so a {{PROBLEM}} inside the job prompt is filled too; "$1"
    // stays literal because replace is not regex-based.
    assertThat(out).startsWith("A has $1 and Its last attempt:").contains(" B Its last attempt:");
  }

  @Test
  void theBundledRepairTemplateHasBothPlaceholders() {
    String out =
        Prompts.repair(Resources.text("repair-prompt.txt"), "THE JOB", Attempt.failed("X", "Y"));

    assertThat(out).contains("THE JOB").contains("Compiler errors:\nY").doesNotContain("{{");
  }
}
