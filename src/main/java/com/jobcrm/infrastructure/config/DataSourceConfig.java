package com.jobcrm.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    config.setConnectionInitSql(
        "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; "
            + "PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");
    config.setDriverClassName(driverClass);
    config.setPoolName(poolName);
    config.setMaximumPoolSize(poolSize);
    return new HikariDataSource(config);
  }

  @Bean
  public Flyway flyWay(@Qualifier("writePool") DataSource writePool) {
    Flyway f = Flyway.configure().dataSource(writePool).locations(flyWayLocations).load();
    f.migrate();
    return f;
  }
}
