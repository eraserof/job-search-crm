package com.jobcrm.infrastructure.persistence;

/**
 * Unchecked exception signalling that a persistence operation failed and could not be recovered —
 * either because the error was non-retryable (e.g. a constraint violation) or because the retry
 * budget was exhausted on transient contention.
 *
 * <p>It wraps the originating {@link java.sql.SQLException} as its cause so the SQLite error code
 * and stack trace are preserved for logging and debugging. Being unchecked ({@code extends
 * RuntimeException}) is deliberate: a storage failure propagates untouched through repositories,
 * application services, and the domain — none of which can meaningfully recover from it — straight
 * to the CLI's top-level handler, which decides how to react (retry, keep a local copy, discard).
 * This keeps intermediate layers free of {@code throws SQLException} and of any {@code java.sql}
 * reference, preserving the bounded-context boundaries the ArchUnit rules enforce.
 */
public class PersistenceException extends RuntimeException {

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
