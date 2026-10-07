package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JDBC implementation of {@link CompanyRepository}. */
@Repository
public class JdbcCompanyRepository implements CompanyRepository {

  private final JdbcSupport jdbc;

  JdbcCompanyRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Company> findById(CompanyId id) {
    return jdbc.queryOne(
        "SELECT id, name, domain, created_at, updated_at FROM company WHERE id = ?",
        ps -> ps.setString(1, uuid(id.value())),
        JdbcCompanyRepository::mapRow);
  }

  @Override
  public Optional<Company> findByName(String name) {
    return jdbc.queryOne(
        "SELECT id, name, domain, created_at, updated_at FROM company "
            + "WHERE name = ? COLLATE NOCASE",
        ps -> ps.setString(1, name),
        JdbcCompanyRepository::mapRow);
  }

  @Override
  public Optional<Company> findByDomain(String domain) {
    return jdbc.queryOne(
        "SELECT id, name, domain, created_at, updated_at FROM company WHERE domain = ?",
        ps -> ps.setString(1, domain),
        JdbcCompanyRepository::mapRow);
  }

  @Override
  public java.util.List<Company> findAll() {
    return jdbc.queryList(
        "SELECT id, name, domain, created_at, updated_at FROM company ORDER BY name COLLATE NOCASE",
        JdbcSupport.noParams(),
        JdbcCompanyRepository::mapRow);
  }

  @Override
  public void save(Company company) {
    jdbc.update(
        "INSERT INTO company (id, name, domain, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(id) DO UPDATE SET "
            + "name = excluded.name, domain = excluded.domain, updated_at = excluded.updated_at",
        ps -> {
          ps.setString(1, uuid(company.id().value()));
          ps.setString(2, company.name());
          setNullableString(ps, 3, company.domain().orElse(null));
          ps.setString(4, instant(company.createdAt()));
          ps.setString(5, instant(company.updatedAt()));
        });
  }

  private static Company mapRow(ResultSet rs) throws SQLException {
    return Company.reconstitute(
        new CompanyId(uuid(rs.getString("id"))),
        rs.getString("name"),
        nullableString(rs, "domain"),
        instant(rs.getString("created_at")),
        instant(rs.getString("updated_at")));
  }
}
