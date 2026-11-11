package com.jobcrm.codegen.vcs;

import java.nio.file.Path;

/** The version-control operations auto-commit needs. */
public interface Vcs {

  /** The checked-out branch name, e.g. "feature/codegen-java". */
  String currentBranch() throws VcsException;

  /** True if {@code file} has staged or unstaged changes, or is untracked. */
  boolean isDirty(Path file) throws VcsException;

  /**
   * Commits ONLY {@code file} (anything else staged is left out) and returns the short sha.
   *
   * @param subject first line of the commit message
   * @param body second paragraph of the commit message
   */
  String commitOnly(Path file, String subject, String body) throws VcsException;
}
