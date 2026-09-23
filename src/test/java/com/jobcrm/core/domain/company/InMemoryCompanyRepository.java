package com.jobcrm.core.domain.company;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link CompanyRepository}. Thread-safe for parallel test isolation. */
public final class InMemoryCompanyRepository implements CompanyRepository {

  private final Map<CompanyId, Company> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Company> findById(CompanyId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<Company> findByName(String name) {
    Objects.requireNonNull(name, "name");
    String normalized = name.trim().toLowerCase(Locale.ROOT);
    return byId.values().stream()
        .filter(c -> c.name().toLowerCase(Locale.ROOT).equals(normalized))
        .min(Comparator.comparing(Company::createdAt));
  }

  @Override
  public Optional<Company> findByDomain(String domain) {
    Objects.requireNonNull(domain, "domain");
    String normalized = domain.trim().toLowerCase(Locale.ROOT);
    return byId.values().stream()
        .filter(c -> c.domain().map(d -> d.equals(normalized)).orElse(false))
        .findFirst();
  }

  @Override
  public void save(Company company) {
    Objects.requireNonNull(company, "company");
    byId.put(company.id(), company);
  }

  /** Test helper: full snapshot of stored companies. */
  public List<Company> all() {
    return List.copyOf(byId.values());
  }
}
