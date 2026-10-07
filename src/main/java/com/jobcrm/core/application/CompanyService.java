package com.jobcrm.core.application;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Application service for the {@link Company} aggregate. Thin transactional facade over {@link
 * CompanyRepository}: each method mutates a single aggregate, which the repository persists
 * atomically in its own write transaction. No Spring {@code @Transactional} is used — the
 * repository owns the transaction boundary (see Task 8 notes), and every operation here touches
 * exactly one aggregate.
 */
@Service
public class CompanyService {

  private final CompanyRepository companies;
  private final Clock clock;

  CompanyService(CompanyRepository companies, Clock clock) {
    this.companies = companies;
    this.clock = clock;
  }

  /** Creates and persists a new company. */
  public CompanyId create(String name) {
    Company company = Company.create(name, clock.instant());
    companies.save(company);
    return company.id();
  }

  /** Renames an existing company. Throws {@link UnknownAggregate} if it does not exist. */
  public void renameTo(CompanyId id, String newName) {
    Company company = require(id);
    company.renameTo(newName, clock.instant());
    companies.save(company);
  }

  /** Sets (or clears, when {@code null}) a company's sender domain. */
  public void setDomain(CompanyId id, String domain) {
    Company company = require(id);
    company.setDomain(domain, clock.instant());
    companies.save(company);
  }

  /**
   * Returns the id of the company with this name, creating it if none exists. Name matching is
   * case-insensitive (delegated to the repository). Used by ingest flows that mention a company by
   * name before it is known.
   */
  public CompanyId findOrCreateByName(String name) {
    Objects.requireNonNull(name, "name");
    Optional<Company> existing = companies.findByName(name);
    return existing.map(Company::id).orElseGet(() -> create(name));
  }

  public Optional<Company> findById(CompanyId id) {
    return companies.findById(id);
  }

  private Company require(CompanyId id) {
    return companies
        .findById(id)
        .orElseThrow(() -> new UnknownAggregate("company", id.value().toString()));
  }
}
