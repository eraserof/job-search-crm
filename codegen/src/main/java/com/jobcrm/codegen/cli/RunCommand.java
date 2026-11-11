package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.job.JobOptions;
import com.jobcrm.codegen.job.JobOutcome;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.lock.RunLock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Code stage for a whole plan folder: every NN-*.job.json in order, stopping at a failure. */
@Command(name = "run", description = "Run every job in a plan folder, in order.")
final class RunCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Plan folder printed by 'codegen plan'.")
  String planDir;

  @Option(names = "--from", description = "Start at job number N (resume after a fix).")
  int from = 1;

  @Option(names = "--no-auto-commit", description = "Do not commit each job after it compiles.")
  boolean noAutoCommit;

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    try (RunLock lock = RunLock.acquire(tool.lockFile(), "run " + planDir)) {
      Path dir = tool.repo.resolve(planDir);
      List<Path> jobs = jobFiles(dir);
      if (jobs.isEmpty()) {
        tool.out.println("no NN-*.job.json files in " + planDir);
        return 2;
      }
      JobOptions options = JobCommand.options(tool, null, null, false, noAutoCommit);
      int done = 0;
      for (Path job : jobs) {
        int number = Integer.parseInt(job.getFileName().toString().substring(0, 2));
        if (number < from) {
          continue;
        }
        tool.out.println("===== " + job.getFileName() + " =====");
        JobOutcome outcome = JobCommand.runOne(tool, job, options);
        if (!outcome.succeeded()) {
          tool.out.printf(
              "STOPPED at %s (exit %d). %d job(s) succeeded before it.%n",
              job.getFileName(), outcome.status().exitCode(), done);
          tool.out.printf("fix its prompt in %s, then resume with: --from %d%n", planDir, number);
          return outcome.status().exitCode();
        }
        done++;
      }
      tool.out.printf(
          "ALL DONE: %d job(s) compiled%s. next: gradlew test, review the commits.%n",
          done, options.autoCommit() ? ", one commit each" : "");
      return 0;
    } catch (LlmException e) {
      tool.out.println("MODEL UNAVAILABLE: " + e.getMessage());
      return JobOutcome.Status.MODEL_UNAVAILABLE.exitCode();
    }
  }

  /** The folder's {@code NN-*.job.json} files, sorted by name (so by number). */
  static List<Path> jobFiles(Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.getFileName().toString().matches("\\d\\d-.+\\.job\\.json"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + dir, e);
    }
  }
}
