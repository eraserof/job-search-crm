package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the deferred Task 5 items against a real SQLite file:
 *
 * <ul>
 *   <li>Flyway runs {@code V1__init.sql} and creates the expected tables.
 *   <li>WAL mode and foreign keys engage when the connection applies the same pragmas the app sets
 *       in {@code DataSourceConfig}.
 * </ul>
 *
 * <p>Uses plain JDBC + a temp file rather than the full Spring context: faster, and it isolates the
 * migration + pragma behaviour from bean wiring. SQLite requires a real file for WAL — an in-memory
 * DB cannot be used here.
 */
class SchemaMigrationTest {

  // The same pragmas DataSourceConfig applies via Hikari connectionInitSql. Executed one-by-one
  // here because a raw JDBC Statement.execute only runs the first statement of a multi-statement
  // string (Hikari handles the combined string specially).
  private static final String[] INIT_PRAGMAS = {
    "PRAGMA journal_mode=WAL",
    "PRAGMA foreign_keys=ON",
    "PRAGMA busy_timeout=5000",
    "PRAGMA synchronous=NORMAL",
  };

  private static void applyInitPragmas(Statement s) throws java.sql.SQLException {
    for (String pragma : INIT_PRAGMAS) {
      s.execute(pragma);
    }
  }

  @TempDir Path tempDir;

  private String jdbcUrl;

  @BeforeEach
  void setUp() {
    Path dbFile = tempDir.resolve("schema-test.db");
    jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    Flyway.configure()
        .dataSource(jdbcUrl, "", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @AfterEach
  void tearDown() throws Exception {
    // WAL leaves -wal and -shm sidecars next to the db; TempDir cleans the dir, but delete the
    // sidecars explicitly in case the OS holds a handle briefly.
    Files.deleteIfExists(tempDir.resolve("schema-test.db-wal"));
    Files.deleteIfExists(tempDir.resolve("schema-test.db-shm"));
  }

  @Test
  void migrationCreatesAllCoreTables() throws Exception {
    List<String> tables = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(jdbcUrl);
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' "
                    + "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'flyway_%' "
                    + "ORDER BY name")) {
      while (rs.next()) {
        tables.add(rs.getString("name"));
      }
    }

    assertThat(tables)
        .containsExactlyInAnyOrder(
            "calendar_cursor",
            "company",
            "contact",
            "contact_email",
            "document",
            "draft_message",
            "event",
            "event_participant",
            "gmail_cursor",
            "interaction",
            "interaction_contact",
            "opportunity",
            "opportunity_contact",
            "task");
  }

  @Test
  void opportunityTableHasExpectedColumns() throws Exception {
    List<String> columns = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(jdbcUrl);
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("PRAGMA table_info('opportunity')")) {
      while (rs.next()) {
        columns.add(rs.getString("name"));
      }
    }

    assertThat(columns)
        .containsExactlyInAnyOrder(
            "id", "company_id", "role", "stage", "resume_document_id", "created_at", "updated_at");
  }

  @Test
  void connectionWithInitPragmasEnablesWalAndForeignKeys() throws Exception {
    try (Connection c = DriverManager.getConnection(jdbcUrl);
        Statement s = c.createStatement()) {
      applyInitPragmas(s);

      try (ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
      }
      try (ResultSet rs = s.executeQuery("PRAGMA foreign_keys")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1);
      }
    }
  }

  @Test
  void foreignKeyConstraintIsEnforcedWhenEnabled() throws Exception {
    try (Connection c = DriverManager.getConnection(jdbcUrl);
        Statement s = c.createStatement()) {
      applyInitPragmas(s);

      // opportunity.company_id references company(id); inserting a dangling reference must fail.
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  s.execute(
                      "INSERT INTO opportunity (id, company_id, role, stage, created_at, updated_at)"
                          + " VALUES ('o1', 'missing-company', 'Engineer', 'APPLIED',"
                          + " '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')"))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }
}
