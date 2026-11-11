package com.jobcrm.codegen.job;

/**
 * Per-run settings for {@link JobRunner}.
 *
 * @param codeAttempts code-stage attempts per round, at least 1
 * @param repairRounds repair rounds after a failed round; 0 disables escalation
 * @param compile false splices without compiling (and then never commits)
 * @param autoCommit commit the target after a successful compile
 */
public record JobOptions(int codeAttempts, int repairRounds, boolean compile, boolean autoCommit) {

  public JobOptions {
    codeAttempts = Math.max(1, codeAttempts);
    repairRounds = Math.max(0, repairRounds);
    autoCommit = autoCommit && compile;
  }
}
