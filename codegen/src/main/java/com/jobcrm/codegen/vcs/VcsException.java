package com.jobcrm.codegen.vcs;

/** A git command failed; the message includes the command's output. */
public final class VcsException extends Exception {
  public VcsException(String message) {
    super(message);
  }

  public VcsException(String message, Throwable cause) {
    super(message, cause);
  }
}
