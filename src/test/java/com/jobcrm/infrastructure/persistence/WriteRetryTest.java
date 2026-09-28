package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

public class WriteRetryTest {

  @Test
  void throwsTwiceThenSucceeds() throws SQLException {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);
    String result =
        retry.execute(
            () -> {
              int current = attempts.incrementAndGet();
              if (current < 3) throw new SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY);
              return "Done";
            });

    assertThat(result).isEqualTo("Done");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void throwErrorAfterTwoRetryAttempts() throws SQLException {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    assertThatThrownBy(
            () ->
                retry.execute(
                    () -> {
                      int currentAttempt = attempts.incrementAndGet();
                      if (currentAttempt < 2)
                        throw new SQLiteException("Busy", SQLiteErrorCode.SQLITE_BUSY);
                      throw new SQLiteException("Error", SQLiteErrorCode.SQLITE_ERROR);
                    }))
        .isInstanceOf(SQLiteException.class);
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void throwErrorAfterExhaustingRetryAttempts() throws SQLException {
    AtomicInteger attempts = new AtomicInteger();
    WriteRetry retry = new WriteRetry(3);

    assertThatThrownBy(
            () ->
                retry.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new SQLiteException("Busy", SQLiteErrorCode.SQLITE_BUSY);
                    }))
        .isInstanceOf(SQLiteException.class);
    assertThat(attempts.get()).isEqualTo(3);
  }
}
