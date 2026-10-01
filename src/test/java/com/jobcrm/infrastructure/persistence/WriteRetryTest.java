package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

class WriteRetryTest {

  @Test
  void succeedsAfterTwoRetries() {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    String result =
        retry.execute(
            () -> {
              int current = attempts.incrementAndGet();
              if (current < 3) {
                throw new SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY);
              }
              return "Done";
            });

    assertThat(result).isEqualTo("Done");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void nonRetryableErrorIsTranslatedAndThrownImmediately() {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    assertThatThrownBy(
            () ->
                retry.execute(
                    () -> {
                      int current = attempts.incrementAndGet();
                      if (current < 2) {
                        throw new SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY);
                      }
                      throw new SQLiteException("constraint", SQLiteErrorCode.SQLITE_CONSTRAINT);
                    }))
        .isInstanceOf(PersistenceException.class)
        .hasCauseInstanceOf(SQLiteException.class);

    // One retry on BUSY, then the non-retryable CONSTRAINT stops it — no further attempts.
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void exhaustingRetriesIsTranslatedAndThrown() {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    assertThatThrownBy(
            () ->
                retry.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY);
                    }))
        .isInstanceOf(PersistenceException.class)
        .hasCauseInstanceOf(SQLiteException.class);

    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void lockedCodeIsAlsoRetried() {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    String result =
        retry.execute(
            () -> {
              int current = attempts.incrementAndGet();
              if (current < 2) {
                throw new SQLiteException("locked", SQLiteErrorCode.SQLITE_LOCKED);
              }
              return "Done";
            });

    assertThat(result).isEqualTo("Done");
    assertThat(attempts.get()).isEqualTo(2);
  }
}
