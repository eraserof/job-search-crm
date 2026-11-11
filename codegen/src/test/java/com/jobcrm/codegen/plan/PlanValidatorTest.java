package com.jobcrm.codegen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.codegen.Repo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanValidatorTest {

  @TempDir Path root;
  private Repo repo;

  @BeforeEach
  void files() {
    repo = new Repo(root);
    repo.write(
        root.resolve("A.java"), "class A {\n  int f() { return 0; }\n  int g() { return 0; }\n}\n");
    repo.write(root.resolve("Broken.java"), "class B { void f( }");
  }

  @Test
  void aGoodPlanHasNoErrors() {
    assertThat(PlanValidator.validate(plan(job("f", "A.java", "int f()")), repo)).isEmpty();
  }

  @Test
  void anEmptyPlanIsRejected() {
    assertThat(PlanValidator.validate(new Plan(List.of()), repo))
        .containsExactly("plan has no jobs");
  }

  @Test
  void reportsEveryProblemWithItsMessage() {
    Plan plan =
        plan(
            job("f", "A.java", "int f()"),
            job("f", "A.java", "int g()"),
            job("bad id", "A.java", "int g()"),
            job("missing", "Nope.java", "int f()"),
            job("absent", "A.java", "int h()"),
            job("unparsable", "Broken.java", "void f()"),
            job("notSig", "A.java", "this is not java"));

    assertThat(PlanValidator.validate(plan, repo))
        .containsExactly(
            "duplicate job id 'f'",
            "job id 'bad id' must be letters, digits, _ or -",
            "job 'missing': target not found: Nope.java",
            "job 'absent': signature 'int h()' found 0 times in A.java (must be exactly 1; copy it"
                + " verbatim, without annotations)",
            "job 'unparsable': Broken.java does not parse",
            "job 'notSig': not a method signature: this is not java");
  }

  @Test
  void emptyBehaviorIsRejected() {
    PlanJob job = new PlanJob("f", "A.java", "int f()", "", "", List.of(), List.of());

    assertThat(PlanValidator.validate(plan(job), repo))
        .containsExactly("job 'f': behavior is empty");
  }

  private static Plan plan(PlanJob... jobs) {
    return new Plan(List.of(jobs));
  }

  private static PlanJob job(String id, String target, String signature) {
    return new PlanJob(id, target, signature, "", "", List.of("do it"), List.of());
  }
}
