package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.config.Escalation;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.job.Job;
import com.jobcrm.codegen.job.JobOptions;
import com.jobcrm.codegen.job.JobOutcome;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.lock.RunLock;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Code stage for a single job file. */
@Command(name = "job", description = "Run one job file through the code stage.")
final class JobCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Job file, e.g. codegen/jobs/x.json")
  String jobFile;

  @Option(names = "--code-attempts", description = "Override escalation.codeAttempts.")
  Integer codeAttempts;

  @Option(names = "--repair-rounds", description = "Override escalation.repairRounds (0 = off).")
  Integer repairRounds;

  @Option(names = "--no-compile", description = "Splice without compiling (never commits).")
  boolean noCompile;

  @Option(names = "--no-auto-commit", description = "Do not commit the target after it compiles.")
  boolean noAutoCommit;

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    try (RunLock lock = RunLock.acquire(tool.lockFile(), "job " + jobFile)) {
      Path file = tool.repo.resolve(jobFile);
      JobOutcome outcome =
          runOne(tool, file, options(tool, codeAttempts, repairRounds, noCompile, noAutoCommit));
      return outcome.status().exitCode();
    } catch (LlmException e) {
      tool.out.println("MODEL UNAVAILABLE: " + e.getMessage());
      return JobOutcome.Status.MODEL_UNAVAILABLE.exitCode();
    }
  }

  /** Applies CLI overrides on top of models.json "escalation". */
  static JobOptions options(
      Tool tool,
      Integer codeAttempts,
      Integer repairRounds,
      boolean noCompile,
      boolean noAutoCommit) {
    Escalation e = tool.config.effectiveEscalation();
    return new JobOptions(
        codeAttempts != null ? codeAttempts : e.codeAttempts(),
        repairRounds != null ? repairRounds : e.repairRounds(),
        !noCompile,
        !noAutoCommit);
  }

  /** Pre-flights the models, runs the job, and prints its outcome. */
  static JobOutcome runOne(Tool tool, Path file, JobOptions options) throws LlmException {
    Job job = Job.load(file, tool.json);
    ResolvedStage code = tool.config.resolve(job.stage());
    tool.requireModel(code);
    ResolvedStage repair = null;
    if (options.repairRounds() > 0 && tool.config.hasStage("repair")) {
      repair = tool.config.resolve("repair");
      try {
        tool.requireModel(repair);
      } catch (LlmException e) {
        // Not fatal: the code stage may succeed without escalating.
        tool.out.println("warning: repair stage unavailable (" + e.getMessage() + ")");
      }
    }
    JobOutcome outcome = tool.jobRunner().run(file, job, code, repair, options);
    tool.out.println(outcome.status() + ": " + outcome.message());
    tool.out.flush();
    return outcome;
  }
}
