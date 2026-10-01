package com.jobcrm.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;

/**
 * Builds the two SQLite connection pools and the Flyway migrator.
 *
 * <p>Per-connection pragmas (WAL, foreign keys, busy timeout, synchronous) are set via {@link
 * SQLiteConfig} data-source properties rather than Hikari's {@code connectionInitSql}. Reason: with
 * sqlite-jdbc, a semicolon-joined {@code connectionInitSql} string only executes the first
 * statement, so {@code foreign_keys=ON} silently never applied — see {@code
 * HikariInitSqlProbeTest}. {@code SQLiteConfig} applies every pragma reliably on each new
 * connection.
 */
@Configuration
public class DataSourceConfig {

  @Value("${app.datasource.jdbc-url}")
  private String dbUrl;

  @Value("${app.datasource.driver-class-name}")
  private String driverClass;

  @Value("${app.datasource.flyway.locations}")
  private String flyWayLocations;

  @Bean("writePool")
  public DataSource writeDataSource() {
    return buildSource("WritePool", 1);
  }

  @Bean("readPool")
  public DataSource readDataSource() {
    return buildSource("ReadPool", 4);
  }

  private DataSource buildSource(String poolName, int poolSize) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dbUrl);
    config.setDriverClassName(driverClass);
    config.setPoolName(poolName);
    config.setMaximumPoolSize(poolSize);
    config.setDataSourceProperties(sqlitePragmas());
    return new HikariDataSource(config);
  }

  /**
   * Per-connection SQLite pragmas, expressed as data-source properties so sqlite-jdbc applies all
   * of them on every connection (unlike a multi-statement {@code connectionInitSql}).
   */
  private static Properties sqlitePragmas() {
    SQLiteConfig pragmas = new SQLiteConfig();
    pragmas.setJournalMode(SQLiteConfig.JournalMode.WAL);
    pragmas.enforceForeignKeys(true);
    pragmas.setBusyTimeout(5000);
    pragmas.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    return pragmas.toProperties();
  }

  @Bean
  public Flyway flyWay(@Qualifier("writePool") DataSource writePool) {
    Flyway f = Flyway.configure().dataSource(writePool).locations(flyWayLocations).load();
    f.migrate();
    return f;
  }
}
