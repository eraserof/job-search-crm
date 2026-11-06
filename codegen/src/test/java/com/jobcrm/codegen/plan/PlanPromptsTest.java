package com.jobcrm.codegen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
  void contextBlockInlinesEachFileUnderAOneLineHeader(@TempDir Path dir) {
    Repo repo = new Repo(dir);
    repo.write(dir.resolve("a/A.java"), "class A {}");
    repo.write(dir.resolve("b.sql"), "select 1;");

    assertThat(PlanPrompts.contextBlock(repo, List.of("a/A.java", "b.sql")))
        .isEqualTo(
            "\n----- FILE: a/A.java -----\nclass A {}\n\n----- FILE: b.sql -----\nselect 1;\n");
    assertThat(PlanPrompts.contextBlock(repo, List.of())).isEmpty();
  }

  @Test
  void extractJsonFindsTheOutermostObject() {
    assertThat(PlanPrompts.extractJson("<think>{no}</think>Sure:\n```json\n{\"jobs\": [{}]}\n```"))
        .contains("{\"jobs\": [{}]}");
    assertThat(PlanPrompts.extractJson("no json here")).isEmpty();
    assertThat(PlanPrompts.extractJson("} then {")).isEmpty();
  }

  @Test
  void responseFormatWrapsTheSchema() throws Exception {
    ObjectMapper json = new ObjectMapper();

    String out =
        json.writeValueAsString(
            PlanPrompts.responseFormat(json, json.readTree("{\"type\":\"object\"}")));

    assertThat(out)
        .isEqualTo(
            "{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"plan\",\"strict\":true,"
                + "\"schema\":{\"type\":\"object\"}}}");
  }

  @Test
  void codePromptLaysOutEverySection() {
    PlanJob job =
        new PlanJob(
            "f", "A.java", "int f()", "class A {}", "none", List.of("step one", "step two"), null);

    assertThat(PlanPrompts.codePrompt("RULES", job))
        .isEqualTo(
            "RULES\n\n=== The class this method belongs to (relevant fields) ===\nclass A {}\n\n"
                + "=== Types and methods you may use ===\nnone\n\n=== Contract ===\n"
                + "Implement:  int f()\n\nBehavior:\n- step one\n- step two\n");
  }

  @Test
  void theRealTemplateYieldsOnlyTheRules() throws Exception {
    // Gradle runs codegen tests with codegen/ as the working directory.
    String template = Files.readString(Path.of("TEMPLATE.txt"));

    String rules = PlanPrompts.rulesHeader(template);

    assertThat(rules).contains("Rules:").doesNotContain("=== The class").doesNotEndWith("\n");
  }
}
