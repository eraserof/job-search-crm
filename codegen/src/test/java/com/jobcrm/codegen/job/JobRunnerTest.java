package com.jobcrm.codegen.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.build.Compiler;
import com.jobcrm.codegen.config.Platform;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.llm.ChatClient;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.metrics.CallLabel;
import com.jobcrm.codegen.vcs.Vcs;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobRunnerTest {

  private static final String TARGET = "src/A.java";
  private static final String SIGNATURE = "int answer()";
  private static final String ORIGINAL =
      """
      class A {
        int answer() {
          throw new UnsupportedOperationException();
        }
      }
      """;

  @TempDir Path root;

  private Repo repo;
  private final FakeVcs vcs = new FakeVcs();
  private final Deque<Object> replies = new ArrayDeque<>();
  private final List<String> prompts = new ArrayList<>();
  private final Deque<Boolean> compiles = new ArrayDeque<>();
  private final StringWriter console = new StringWriter();

  private final ChatClient chat =
      (stage, prompt, format) -> {
        prompts.add(stage.name() + ": " + prompt);
        Object next = replies.removeFirst();
        if (next instanceof LlmException e) {
          throw e;
        }
        return (String) next;
      };

  private final Compiler compiler =
      () -> {
        boolean ok = compiles.removeFirst();
        return new Compiler.CompileResult(
            ok, ok ? "" : "src\\A.java:3: error: cannot find symbol\n  symbol: x\n1 error");
      };

  @BeforeEach
  void files() throws Exception {
    repo = new Repo(root);
    repo.write(root.resolve(TARGET), ORIGINAL);
    repo.write(
        root.resolve("prompts/a.prompt.txt"),
        "implement answer\n=== Contract ===\nImplement:  int answer()");
  }

  // ---------- refusals: nothing is touched ----------

  @Test
  void refusesToAutoCommitOnMain() {
    vcs.branch = "main";

    JobOutcome outcome = run(options(2, 0, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.REFUSED);
    assertThat(outcome.message()).contains("refusing to commit on 'main'");
    assertUntouched();
  }

  @Test
  void refusesWhenTheTargetHasUncommittedChanges() {
    vcs.dirty = true;

    JobOutcome outcome = run(options(2, 0, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.REFUSED);
    assertThat(outcome.message()).contains("uncommitted changes");
    assertUntouched();
  }

  @Test
  void refusesWhenTheSignatureIsNotInTheTarget() {
    JobOutcome outcome =
        run(
            new Job("code", TARGET, "int missing()", "prompts/a.prompt.txt", List.of()),
            options(2, 0, false));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.REFUSED);
    assertThat(outcome.message()).contains("found 0 times");
    assertUntouched();
  }

  @Test
  void refusesEscalationWithoutARepairStage() {
    JobOutcome outcome =
        new JobRunner(repo, chat, compiler, vcs, new PrintWriter(console))
            .run(root.resolve("01-a.job.json"), job(), stage("code"), null, options(1, 1, false));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.REFUSED);
    assertUntouched();
  }

  // ---------- model failures restore the file and keep the trail ----------

  @Test
  void restoresTheTargetWhenTheModelIsUnavailable() throws Exception {
    replies.add(LlmException.retryable("cannot connect"));

    JobOutcome outcome = run(options(2, 1, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.MODEL_UNAVAILABLE);
    assertThat(outcome.status().exitCode()).isEqualTo(4);
    assertThat(Files.readString(root.resolve(TARGET))).isEqualTo(ORIGINAL);
    assertThat(outcome.runDir().resolve("original.java.bak")).hasContent(ORIGINAL);
    assertThat(outcome.runDir().resolve("r0-a1.prompt.txt")).exists();
    assertThat(vcs.commits).isEmpty();
  }

  // ---------- the full ladder (needs the generated helpers) ----------

  @Test
  void compilesOnTheFirstTryAndCommitsOnlyTheTarget() throws Exception {
    replies.add("```java\nreturn 42;\n```");
    compiles.add(true);

    JobOutcome outcome = run(options(2, 1, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.COMPILED);
    assertThat(Files.readString(root.resolve(TARGET))).contains("return 42;").doesNotContain("```");
    assertThat(vcs.commits).containsExactly("codegen: A.answer (1 attempt(s))");
  }

  @Test
  void escalatesToRepairThenSavesTheRepairedPrompt() throws Exception {
    replies.add("return x;");
    replies.add("return x;");
    replies.add(
        "=== REVISED PROMPT ===\nimplement answer: return the literal 42\n=== Contract ===\n"
            + "Implement:  int answer()\n=== END ===");
    replies.add("return 42;");
    compiles.add(false);
    compiles.add(false);
    compiles.add(true);

    JobOutcome outcome = run(options(2, 1, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.COMPILED);
    assertThat(outcome.attempts()).isEqualTo(3);
    assertThat(prompts.get(1)).contains("failed to compile").doesNotContain("return x;");
    assertThat(prompts.get(2)).startsWith("repair: ");
    assertThat(root.resolve("prompts/a.prompt.txt")).content().contains("the literal 42");
    assertThat(root.resolve("prompts/a.prompt.txt.orig")).exists();
    assertThat(vcs.commits).containsExactly("codegen: A.answer (3 attempt(s), repaired)");
  }

  @Test
  void modelCallsSeeTheCommitIdAsTheirLabelAndTheLabelIsClearedAfterwards() {
    CallLabel label = new CallLabel();
    List<String> seen = new ArrayList<>();
    ChatClient watching =
        (stage, prompt, format) -> {
          seen.add(stage.name() + "=" + label.current());
          return (String) replies.removeFirst();
        };
    replies.add("return x;");
    replies.add(
        "=== REVISED PROMPT ===\nimplement answer\n=== Contract ===\nImplement:  int answer()\n=== END ===");
    replies.add("return 42;");
    compiles.add(false);
    compiles.add(true);

    new JobRunner(repo, watching, compiler, vcs, new PrintWriter(console), label)
        .run(
            root.resolve("01-a.job.json"),
            job(),
            stage("code"),
            stage("repair"),
            options(1, 1, true));

    assertThat(seen).containsExactly("code=A.answer", "repair=A.answer", "code=A.answer");
    assertThat(label.current()).isNull();
  }

  @Test
  void theLabelIsClearedWhenTheModelFails() {
    CallLabel label = new CallLabel();
    ChatClient failing =
        (stage, prompt, format) -> {
          throw LlmException.retryable("cannot connect");
        };

    new JobRunner(repo, failing, compiler, vcs, new PrintWriter(console), label)
        .run(
            root.resolve("01-a.job.json"),
            job(),
            stage("code"),
            stage("repair"),
            options(1, 0, false));

    assertThat(label.current()).isNull();
  }

  // ---------- bug catcher, layer 1: warns, never blocks ----------

  private static final String WITH_CONTRACT =
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
          throw new UnsupportedOperationException();
        }
      }
      """;

  @Test
  void aMissingContractLiteralIsWrittenToReviewTxtButTheCommitStillHappens() throws Exception {
    repo.write(root.resolve(TARGET), WITH_CONTRACT);
    replies.add("return 42;");
    compiles.add(true);

    JobOutcome outcome = run(options(1, 0, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.COMPILED);
    assertThat(outcome.message()).contains("1 review warning(s)");
    assertThat(outcome.runDir().resolve("review.txt"))
        .content()
        .contains("review of A.answer")
        .contains("WARN \"forty-two\"");
    assertThat(vcs.commits).containsExactly("codegen: A.answer (1 attempt(s))");
  }

  @Test
  void aBodyWithTheContractLiteralWritesNoReviewFile() throws Exception {
    repo.write(root.resolve(TARGET), WITH_CONTRACT);
    replies.add("String s = \"forty-two\";\nreturn 42;");
    compiles.add(true);

    JobOutcome outcome = run(options(1, 0, true));

    assertThat(outcome.status()).isEqualTo(JobOutcome.Status.COMPILED);
    assertThat(outcome.message()).doesNotContain("review");
    assertThat(outcome.runDir().resolve("review.txt")).doesNotExist();
    assertThat(vcs.commits).hasSize(1);
  }

  // ---------- helpers ----------

  private JobOutcome run(JobOptions options) {
    return run(job(), options);
  }

  private JobOutcome run(Job job, JobOptions options) {
    return new JobRunner(repo, chat, compiler, vcs, new PrintWriter(console))
        .run(root.resolve("01-a.job.json"), job, stage("code"), stage("repair"), options);
  }

  private static Job job() {
    return new Job("code", TARGET, SIGNATURE, "prompts/a.prompt.txt", List.of());
  }

  private static JobOptions options(int codeAttempts, int repairRounds, boolean autoCommit) {
    return new JobOptions(codeAttempts, repairRounds, true, autoCommit);
  }

  private static ResolvedStage stage(String name) {
    return new ResolvedStage(
        name,
        "fake",
        new Platform("ollama", "http://fake/v1", null),
        name + "-model",
        0.2,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private void assertUntouched() {
    assertThat(root.resolve(TARGET)).hasContent(ORIGINAL);
    assertThat(root.resolve("codegen/drafts/runs")).doesNotExist();
    assertThat(prompts).isEmpty();
    assertThat(vcs.commits).isEmpty();
  }

  private static final class FakeVcs implements Vcs {
    String branch = "feature/x";
    boolean dirty;
    final List<String> commits = new ArrayList<>();

    @Override
    public String currentBranch() {
      return branch;
    }

    @Override
    public boolean isDirty(Path file) {
      return dirty;
    }

    @Override
    public String commitOnly(Path file, String subject, String body) {
      commits.add(subject);
      return "abc1234";
    }
  }
}
