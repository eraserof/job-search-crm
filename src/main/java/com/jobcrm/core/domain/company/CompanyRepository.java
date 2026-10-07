package com.jobcrm.core.domain.company;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Company} aggregate roots. */
public interface CompanyRepository {

  Optional<Company> findById(CompanyId id);

  /** All companies, ordered by name. Used by the CLI {@code company list}. */
  List<Company> findAll();

  /** Case-insensitive name lookup. Names are unique across the table. */
  Optional<Company> findByName(String name);

  /** Sender-domain lookup used by the email ingest agent to route inbound mail. */
  Optional<Company> findByDomain(String domain);

  void save(Company company);
}
