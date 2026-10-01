package com.jobcrm.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;

/**
 * Shared base for the JDBC repository tests. Each test runs against a real SQLite file in a {@link
 * TempDir}, with Flyway migrations applied in {@link #setUp()} and the same per-connection pragmas
 * {@code DataSourceConfig} uses in production (WAL, foreign keys, busy timeout, synchronous
 * NORMAL).
 *
 * <p>SQLite behaviour (WAL, {@code SQLITE_BUSY}, TEXT affinity) matters, so no in-memory substitute
 * is used — see development-guidelines.md § Repository tests.
 */
abstract class AbstractRepositoryTest {

  @TempDir Path tempDir;

  private HikariDataSource readPool;
  private HikariDataSource writePool;
  private JdbcSupport jdbc;

  @BeforeEach
  void setUp() {
    Path dbFile = tempDir.resolve("repo-test.db");
    String jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();

    Flyway.configure()
        .dataSource(jdbcUrl, "", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    readPool = buildPool(jdbcUrl, "ReadPool", 4);
    writePool = buildPool(jdbcUrl, "WritePool", 1);
    // WriteRetry and JdbcSupport have package-private constructors; these tests live in the same
    // package (com.jobcrm.infrastructure.persistence) so direct construction is allowed.
    WriteRetry writeRetry = new WriteRetry(3);
    jdbc = new JdbcSupport(readPool, writePool, writeRetry);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (readPool != null) {
      readPool.close();
    }
    if (writePool != null) {
      writePool.close();
    }
    // WAL leaves -wal and -shm sidecars; TempDir cleans the directory, but delete them explicitly
    // in case the OS briefly holds a handle.
    Files.deleteIfExists(tempDir.resolve("repo-test.db-wal"));
    Files.deleteIfExists(tempDir.resolve("repo-test.db-shm"));
  }

  protected JdbcSupport jdbc() {
    return jdbc;
  }

  // ---- Fixture helpers: insert parent rows via raw SQL matching V1__init.sql exactly, so tests
  // for child aggregates do not depend on other repositories (and so FK constraints are satisfied).

  protected void insertCompany(java.util.UUID companyId, String name, String createdAt) {
    jdbc()
        .update(
            "INSERT INTO company (id, name, domain, created_at) VALUES (?, ?, ?, ?)",
            ps -> {
              ps.setString(1, companyId.toString());
              ps.setString(2, name);
              ps.setString(3, null);
              ps.setString(4, createdAt);
            });
  }

  protected void insertOpportunity(
      java.util.UUID opportunityId, java.util.UUID companyId, String role, String createdAt) {
    jdbc()
        .update(
            "INSERT INTO opportunity (id, company_id, role, stage, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'APPLIED', ?, ?)",
            ps -> {
              ps.setString(1, opportunityId.toString());
              ps.setString(2, companyId.toString());
              ps.setString(3, role);
              ps.setString(4, createdAt);
              ps.setString(5, createdAt);
            });
  }

  protected void insertContact(java.util.UUID contactId, String displayName, String createdAt) {
    jdbc()
        .update(
            "INSERT INTO contact (id, display_name, default_roles, created_at, updated_at) "
                + "VALUES (?, ?, '[]', ?, ?)",
            ps -> {
              ps.setString(1, contactId.toString());
              ps.setString(2, displayName);
              ps.setString(3, createdAt);
              ps.setString(4, createdAt);
            });
  }

  protected long countRows(String table) {
    return jdbc()
        .queryList(
            "SELECT COUNT(*) AS n FROM " + table, JdbcSupport.noParams(), rs -> rs.getLong("n"))
        .get(0);
  }

  private static HikariDataSource buildPool(String jdbcUrl, String poolName, int poolSize) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setDriverClassName("org.sqlite.JDBC");
    config.setPoolName(poolName);
    config.setMaximumPoolSize(poolSize);
    config.setDataSourceProperties(sqlitePragmas());
    return new HikariDataSource(config);
  }

  private static Properties sqlitePragmas() {
    SQLiteConfig pragmas = new SQLiteConfig();
    pragmas.setJournalMode(SQLiteConfig.JournalMode.WAL);
    pragmas.enforceForeignKeys(true);
    pragmas.setBusyTimeout(5000);
    pragmas.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    return pragmas.toProperties();
  }
}
