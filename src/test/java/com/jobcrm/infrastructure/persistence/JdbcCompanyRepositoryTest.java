package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JdbcCompanyRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void savesAndFindsByIdWithAllFields() {
    JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc());
    Company company = Company.create("Acme Corp", T0);
    company.setDomain("acme.com", T0);
    repo.save(company);

    Optional<Company> found = repo.findById(company.id());

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(company.id());
    assertThat(found.get().name()).isEqualTo("Acme Corp");
    assertThat(found.get().domain()).contains("acme.com");
    assertThat(found.get().createdAt()).isEqualTo(T0);
  }

  @Test
  void findByNameIsCaseInsensitive() {
    JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc());
    repo.save(Company.create("Globex", T0));

    assertThat(repo.findByName("globex")).isPresent();
  }

  @Test
  void findByDomainReturnsMatch() {
    JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc());
    Company company = Company.create("Initech", T0);
    company.setDomain("initech.io", T0);
    repo.save(company);

    assertThat(repo.findByDomain("initech.io")).isPresent();
  }

  @Test
  void saveTwiceUpdatesInPlace() {
    JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc());
    Company company = Company.create("Umbrella", T0);
    repo.save(company);

    company.renameTo("Umbrella Inc", T0.plusSeconds(60));
    repo.save(company);

    Optional<Company> found = repo.findById(company.id());
    assertThat(found).isPresent();
    assertThat(found.get().name()).isEqualTo("Umbrella Inc");
    assertThat(countRows("company")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    JdbcCompanyRepository repo = new JdbcCompanyRepository(jdbc());
    assertThat(repo.findById(CompanyId.newId())).isEmpty();
  }
}
