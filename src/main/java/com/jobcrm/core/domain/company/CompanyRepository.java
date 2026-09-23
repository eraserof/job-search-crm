package com.jobcrm.core.domain.company;

import java.util.Optional;

/** Persistence port for {@link Company} aggregate roots. */
public interface CompanyRepository {

  Optional<Company> findById(CompanyId id);

  /** Case-insensitive name lookup. Names are unique across the table. */
  Optional<Company> findByName(String name);

  /** Sender-domain lookup used by the email ingest agent to route inbound mail. */
  Optional<Company> findByDomain(String domain);

  void save(Company company);
}
