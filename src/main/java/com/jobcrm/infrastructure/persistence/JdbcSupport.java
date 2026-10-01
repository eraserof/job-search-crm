package com.jobcrm.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Shared JDBC plumbing for the hand-written repositories. Centralises the repetitive connection /
 * statement / resource lifecycle so each repository only has to supply its SQL, parameter binding,
 * and row mapping.
 *
 * <p>Reads go through the read pool directly. Writes go through the write pool wrapped in {@link
 * WriteRetry}, so transient {@code SQLITE_BUSY}/{@code SQLITE_LOCKED} contention is retried and any
 * genuine failure surfaces as an unchecked {@link PersistenceException}. Read failures are
 * translated to {@link PersistenceException} too, so callers never see a checked {@link
 * SQLException}.
 */
@Component
public class JdbcSupport {

  private final DataSource readPool;
  private final DataSource writePool;
  private final WriteRetry writeRetry;

  JdbcSupport(
      @Qualifier("readPool") DataSource readPool,
      @Qualifier("writePool") DataSource writePool,
      WriteRetry writeRetry) {
    this.readPool = readPool;
    this.writePool = writePool;
    this.writeRetry = writeRetry;
  }

  /** Binds parameters onto a prepared statement. */
  @FunctionalInterface
  public interface ParameterBinder {
    void bind(PreparedStatement ps) throws SQLException;
  }

  /** Maps the current row of a result set to a domain object. */
  @FunctionalInterface
  public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }

  private static final ParameterBinder NO_PARAMS = ps -> {};

  // ---------- Reads (read pool, no retry) ----------

  /** Runs a query and maps every row. */
  public <T> List<T> queryList(String sql, ParameterBinder binder, RowMapper<T> mapper) {
    try (Connection c = readPool.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      binder.bind(ps);
      try (ResultSet rs = ps.executeQuery()) {
        List<T> out = new ArrayList<>();
        while (rs.next()) {
          out.add(mapper.map(rs));
        }
        return out;
      }
    } catch (SQLException e) {
      throw new PersistenceException("query failed: " + sql, e);
    }
  }

  /** Runs a query expected to return at most one row. */
  public <T> Optional<T> queryOne(String sql, ParameterBinder binder, RowMapper<T> mapper) {
    List<T> rows = queryList(sql, binder, mapper);
    if (rows.size() > 1) {
      throw new PersistenceException(
          "expected at most one row but got " + rows.size() + " for: " + sql, null);
    }
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  // ---------- Writes (write pool, retried) ----------

  /**
   * Runs one or more statements as a single transaction on the write pool, retried on transient
   * contention. The supplied work receives the connection; auto-commit is disabled and the
   * transaction is committed if the work returns normally, rolled back on any exception.
   */
  public void inWriteTransaction(TransactionalWork work) {
    writeRetry.execute(
        () -> {
          try (Connection c = writePool.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
              work.run(c);
              c.commit();
              return null;
            } catch (SQLException | RuntimeException e) {
              c.rollback();
              throw e;
            } finally {
              c.setAutoCommit(previousAutoCommit);
            }
          }
        });
  }

  /** A unit of transactional write work operating on a provided connection. */
  @FunctionalInterface
  public interface TransactionalWork {
    void run(Connection connection) throws SQLException;
  }

  /** Convenience: execute a single parameterised write statement in its own transaction. */
  public void update(String sql, ParameterBinder binder) {
    inWriteTransaction(
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
          }
        });
  }

  public static ParameterBinder noParams() {
    return NO_PARAMS;
  }
}
