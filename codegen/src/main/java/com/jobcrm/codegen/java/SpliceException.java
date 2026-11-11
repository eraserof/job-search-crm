package com.jobcrm.codegen.java;

/** The target method could not be located exactly once, so nothing was spliced. */
public final class SpliceException extends RuntimeException {
  public SpliceException(String message) {
    super(message);
  }
}
