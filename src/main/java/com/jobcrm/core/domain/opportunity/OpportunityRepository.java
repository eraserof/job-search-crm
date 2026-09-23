package com.jobcrm.core.domain.opportunity;

import com.jobcrm.core.domain.company.CompanyId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Opportunity} aggregate roots. Concrete implementations live in {@code
 * infrastructure.persistence}.
 */
public interface OpportunityRepository {

  Optional<Opportunity> findById(OpportunityId id);

  List<Opportunity> findByCompany(CompanyId company);

  List<Opportunity> findByStage(Stage stage);

  /**
   * Simple text search over role and (denormalised) company name. Implementation-defined ranking.
   */
  List<Opportunity> searchByText(String query, int limit);

  /**
   * Returns opportunities in a non-terminal stage whose last interaction is at or before {@code
   * threshold}. Used by the stale-loop rule and the {@code StaleDetectAgent}.
   */
  List<Opportunity> findSilentSince(Instant threshold);

  void save(Opportunity opportunity);
}
