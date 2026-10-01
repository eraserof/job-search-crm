package com.jobcrm.infrastructure.persistence;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WriteRetry {
  private int maxAttempts;

  WriteRetry(@Value("${app.datasource.max-attempts}") int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public <T> T execute(SqlOperation<T> command) throws SQLException {
    SQLException lastException = null;

    for (int currentRetry = 0; currentRetry < maxAttempts; currentRetry++) {
      try {
        return command.run();
      } catch (SQLException exception) {
        lastException = exception;
        if (exception.getErrorCode() == 5 || exception.getErrorCode() == 6) {
          try {
            TimeUnit.MILLISECONDS.sleep(currentRetry + 2);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception;
          }
        } else {
          throw exception;
        }
      }
    }
    throw lastException;
  }
}
