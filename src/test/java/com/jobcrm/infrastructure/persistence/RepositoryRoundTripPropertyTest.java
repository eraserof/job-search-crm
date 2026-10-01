package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.Stage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;

/**
 * Property-based coverage of the correctness property "save then findById is a round-trip identity"
 * (development-guidelines.md). Each example runs against its own fresh temp SQLite database so the
 * properties stay independent and jqwik's generation model doesn't share mutable DB state across
 * tries.
 */
class RepositoryRoundTripPropertyTest {

  private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

  @Property(tries = 40)
  void companyRoundTrips(@ForAll("companies") CompanyData data) throws Exception {
    withFreshDb(
        jdbc -> {
          JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc);
          Company company = Company.create(data.name(), BASE);
          if (data.domain() != null) {
            company.setDomain(data.domain(), BASE);
          }
          repo.save(company);

          Optional<Company> loaded = repo.findById(company.id());
          assertThat(loaded).isPresent();
          assertThat(loaded.get().id()).isEqualTo(company.id());
          assertThat(loaded.get().name()).isEqualTo(company.name());
          assertThat(loaded.get().domain()).isEqualTo(company.domain());
          assertThat(loaded.get().createdAt()).isEqualTo(company.createdAt());
          assertThat(loaded.get().updatedAt()).isEqualTo(company.updatedAt());
        });
  }

  @Property(tries = 40)
  void opportunityRoundTrips(
      @ForAll("roles") String role, @ForAll @IntRange(min = 0, max = 5) int stageOrdinal)
      throws Exception {
    withFreshDb(
        jdbc -> {
          JdbcCompanyRepository companies = new JdbcCompanyRepository(jdbc);
          JdbcOpportunityRepository opportunities = new JdbcOpportunityRepository(jdbc);

          Company company = Company.create("Acme " + role.hashCode(), BASE);
          companies.save(company);

          Opportunity opp = Opportunity.create(company.id(), role, BASE);
          // Walk the opportunity to a non-initial stage sometimes, respecting the state machine.
          Stage target = reachableStage(stageOrdinal);
          if (opp.stage() != target && opp.stage().canTransitionTo(target)) {
            opp.advanceStage(target, BASE);
          }
          opportunities.save(opp);

          Optional<Opportunity> loaded = opportunities.findById(opp.id());
          assertThat(loaded).isPresent();
          assertThat(loaded.get().id()).isEqualTo(opp.id());
          assertThat(loaded.get().companyId()).isEqualTo(company.id());
          assertThat(loaded.get().role()).isEqualTo(opp.role());
          assertThat(loaded.get().stage()).isEqualTo(opp.stage());
          // No interactions persisted, so lastInteractionAt derives to createdAt.
          assertThat(loaded.get().lastInteractionAt()).isEqualTo(opp.createdAt());
        });
  }

  private static Stage reachableStage(int ordinal) {
    // A small set reachable from APPLIED in one hop, to keep the generator honest about the
    // transition rules.
    Stage[] oneHop = {
      Stage.APPLIED,
      Stage.RECRUITER_SCREEN,
      Stage.HM_SCREEN,
      Stage.REJECTED,
      Stage.GHOSTED,
      Stage.WITHDRAWN
    };
    return oneHop[ordinal % oneHop.length];
  }

  @Provide
  Arbitrary<CompanyData> companies() {
    Arbitrary<String> names =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20);
    Arbitrary<String> domains =
        Arbitraries.of("stripe.com", "datadog.com", "example.org", null, null);
    return names.flatMap(n -> domains.map(d -> new CompanyData(n, d)));
  }

  @Provide
  Arbitrary<String> roles() {
    return Arbitraries.of(
        "Backend Engineer",
        "Staff SRE",
        "Senior Platform Engineer",
        "Engineering Manager",
        "Data Scientist");
  }

  record CompanyData(String name, String domain) {}

  // ---- per-example fresh database -------------------------------------------------------------

  @FunctionalInterface
  private interface DbWork {
    void run(JdbcSupport jdbc) throws Exception;
  }

  private void withFreshDb(DbWork work) throws Exception {
    Path dir = Files.createTempDirectory("roundtrip-prop");
    Path dbFile = dir.resolve("prop.db");
    String jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    Flyway.configure()
        .dataSource(jdbcUrl, "", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    HikariDataSource readPool = pool(jdbcUrl, "PropRead", 4);
    HikariDataSource writePool = pool(jdbcUrl, "PropWrite", 1);
    try {
      JdbcSupport jdbc = new JdbcSupport(readPool, writePool, new WriteRetry(3));
      work.run(jdbc);
    } finally {
      readPool.close();
      writePool.close();
      Files.deleteIfExists(dir.resolve("prop.db-wal"));
      Files.deleteIfExists(dir.resolve("prop.db-shm"));
      Files.deleteIfExists(dbFile);
      Files.deleteIfExists(dir);
    }
  }

  private static HikariDataSource pool(String jdbcUrl, String name, int size) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setDriverClassName("org.sqlite.JDBC");
    config.setPoolName(name);
    config.setMaximumPoolSize(size);
    SQLiteConfig pragmas = new SQLiteConfig();
    pragmas.setJournalMode(SQLiteConfig.JournalMode.WAL);
    pragmas.enforceForeignKeys(true);
    pragmas.setBusyTimeout(5000);
    pragmas.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    Properties props = pragmas.toProperties();
    config.setDataSourceProperties(props);
    return new HikariDataSource(config);
  }
}
