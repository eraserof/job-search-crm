package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;

/**
 * Guards the pragma-application mechanism used by {@code DataSourceConfig}.
 *
 * <p>Documents a real pitfall found during Task 6: a semicolon-joined {@code connectionInitSql}
 * applies ONLY its first statement through sqlite-jdbc, so {@code foreign_keys=ON} silently never
 * took effect. The fix is to pass pragmas as {@link SQLiteConfig} data-source properties, which
 * this test confirms applies every pragma on each connection.
 */
class HikariInitSqlProbeTest {

  @TempDir Path tempDir;

  @Test
  void sqliteConfigPropertiesApplyAllPragmas() throws Exception {
    SQLiteConfig pragmas = new SQLiteConfig();
    pragmas.setJournalMode(SQLiteConfig.JournalMode.WAL);
    pragmas.enforceForeignKeys(true);
    pragmas.setBusyTimeout(5000);
    pragmas.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("probe.db").toAbsolutePath());
    config.setDriverClassName("org.sqlite.JDBC");
    config.setMaximumPoolSize(1);
    config.setDataSourceProperties(pragmas.toProperties());

    try (HikariDataSource ds = new HikariDataSource(config);
        Connection c = ds.getConnection();
        Statement s = c.createStatement()) {
      try (ResultSet rs = s.executeQuery("PRAGMA foreign_keys")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).as("foreign_keys applied via SQLiteConfig").isEqualTo(1);
      }
      try (ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
      }
    }
  }
}
