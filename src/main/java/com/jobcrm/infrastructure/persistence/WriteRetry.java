package com.jobcrm.infrastructure.persistence;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteErrorCode;

/**
 * Runs a write operation, retrying on transient SQLite contention ({@link
 * SQLiteErrorCode#SQLITE_BUSY} / {@link SQLiteErrorCode#SQLITE_LOCKED}) up to a configured number
 * of attempts.
 *
 * <p>On a non-retryable error, or once the retry budget is exhausted, the originating {@link
 * SQLException} is logged once and rethrown wrapped in an unchecked {@link PersistenceException}.
 * {@code execute} therefore does not declare {@code throws SQLException}: callers (repositories,
 * services) stay free of checked persistence exceptions and the failure propagates to the CLI's
 * top-level handler.
 */
@Component
public class WriteRetry {

  private static final Logger log = LoggerFactory.getLogger(WriteRetry.class);

  private static final int SQLITE_BUSY = SQLiteErrorCode.SQLITE_BUSY.code;
  private static final int SQLITE_LOCKED = SQLiteErrorCode.SQLITE_LOCKED.code;

  private final int maxAttempts;

  WriteRetry(@Value("${app.datasource.max-attempts}") int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public <T> T execute(SqlOperation<T> command) {
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        return command.run();
      } catch (SQLException exception) {
        if (!isRetryable(exception)) {
          throw translate("non-retryable SQL error", exception);
        }
        if (attempt == maxAttempts - 1) {
          throw translate("exhausted " + maxAttempts + " attempts on transient error", exception);
        }
        backoff(attempt, exception);
      }
    }
    // Unreachable: the loop always returns or throws. Present to satisfy the compiler.
    throw new IllegalStateException("retry loop exited without a result");
  }

  private static boolean isRetryable(SQLException exception) {
    int code = exception.getErrorCode();
    return code == SQLITE_BUSY || code == SQLITE_LOCKED;
  }

  private void backoff(int attempt, SQLException exception) {
    try {
      TimeUnit.MILLISECONDS.sleep(attempt + 2L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw translate("interrupted while backing off between retries", exception);
    }
  }

  /** Logs once at the point of failure and wraps the cause in an unchecked exception. */
  private PersistenceException translate(String reason, SQLException cause) {
    log.error("Write operation failed: {} (sqlite code {})", reason, cause.getErrorCode(), cause);
    return new PersistenceException("Write operation failed: " + reason, cause);
  }
}
