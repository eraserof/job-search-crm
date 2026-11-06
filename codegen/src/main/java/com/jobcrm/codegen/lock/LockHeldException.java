package com.jobcrm.codegen.lock;

import java.nio.file.Path;

/** Another codegen run in this checkout holds the lock. */
public final class LockHeldException extends RuntimeException {
  public LockHeldException(Path file, String owner) {
    super(
        "another codegen run is active ("
            + owner
            + "). lock: "
            + file
            + ". if no run is active, delete the lock file.");
  }
}
