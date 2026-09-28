package com.jobcrm.infrastructure.persistence;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlOperation<T> {
  T run() throws SQLException;
}
