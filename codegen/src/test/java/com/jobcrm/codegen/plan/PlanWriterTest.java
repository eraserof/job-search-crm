package com.jobcrm.codegen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.job.Job;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanWriterTest {

  @TempDir Path root;

  @Test
  void writesThePlanAndOneNumberedPromptAndJobPerEntry() throws Exception {
    Repo repo = new Repo(root);
    ObjectMapper json = new ObjectMapper();
    Path dir = root.resolve("codegen/drafts/plans/x");
    Plan plan =
        new Plan(
            List.of(
                new PlanJob(
                    "first",
                    "A.java",
                    "int f()",
                    "ctx",
                    "types",
                    List.of("s"),
                    List.of("java.util.List")),
                new PlanJob(
                    "second", "A.java", "int g()", "ctx", "types", List.of("s"), List.of())));

    List<Path> jobs = PlanWriter.write(plan, dir, "RULES", repo, json);

    assertThat(jobs)
        .containsExactly(dir.resolve("01-first.job.json"), dir.resolve("02-second.job.json"));
    assertThat(json.readValue(dir.resolve("plan.json").toFile(), Plan.class)).isEqualTo(plan);
    assertThat(Files.readString(dir.resolve("01-first.prompt.txt"))).startsWith("RULES\n\n");

    Job first = Job.load(jobs.get(0), json);
    assertThat(first)
        .isEqualTo(
            new Job(
                "code",
                "A.java",
                "int f()",
                "codegen/drafts/plans/x/01-first.prompt.txt",
                List.of("java.util.List")));
  }
}
