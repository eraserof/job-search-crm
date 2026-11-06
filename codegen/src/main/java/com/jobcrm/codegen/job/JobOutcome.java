package com.jobcrm.codegen.job;

import java.nio.file.Path;

/**
 * How a job ended. {@link Status#exitCode()} is the process exit code (same as the PowerShell
 * version, plus 2 and 4).
 *
 * @param runDir nullable when the job failed before the run folder was created
 * @param commit nullable short sha when auto-commit made a commit
 */
public record JobOutcome(Status status, int attempts, Path runDir, String message, String commit) {

  public enum Status {
    /** Compiled, and committed if auto-commit is on. */
    COMPILED(0),
    /** Every attempt failed; the target was restored. */
    FAILED(1),
    /** Refused before touching anything: dirty target, main branch, signature not found. */
    REFUSED(2),
    /** The model asked a question even after repair; the target was restored. */
    NEEDS_CLARIFICATION(3),
    /** A model could not be reached or kept failing; the target was restored. */
    MODEL_UNAVAILABLE(4),
    /** Compiled, but the commit failed; the change is left in place, uncommitted. */
    COMMIT_FAILED(5);

    private final int exitCode;

    Status(int exitCode) {
      this.exitCode = exitCode;
    }

    public int exitCode() {
      return exitCode;
    }
  }

  public boolean succeeded() {
    return status == Status.COMPILED;
  }
}
