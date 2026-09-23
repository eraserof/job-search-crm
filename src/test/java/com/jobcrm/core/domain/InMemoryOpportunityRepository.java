package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link OpportunityRepository}. */
public final class InMemoryOpportunityRepository implements OpportunityRepository {

  private final Map<OpportunityId, Opportunity> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Opportunity> findById(OpportunityId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<Opportunity> findByCompany(CompanyId company) {
    Objects.requireNonNull(company, "company");
    return byId.values().stream().filter(o -> o.companyId().equals(company)).toList();
  }

  @Override
  public List<Opportunity> findByStage(Stage stage) {
    Objects.requireNonNull(stage, "stage");
    return byId.values().stream().filter(o -> o.stage() == stage).toList();
  }

  @Override
  public List<Opportunity> searchByText(String query, int limit) {
    Objects.requireNonNull(query, "query");
    if (limit <= 0) {
      return List.of();
    }
    String q = query.trim().toLowerCase(Locale.ROOT);
    return byId.values().stream()
        .filter(o -> o.role().toLowerCase(Locale.ROOT).contains(q))
        .limit(limit)
        .toList();
  }

  @Override
  public List<Opportunity> findSilentSince(Instant threshold) {
    Objects.requireNonNull(threshold, "threshold");
    return byId.values().stream()
        .filter(o -> !o.stage().isTerminal())
        .filter(
            o ->
                o.lastInteractionAt().isBefore(threshold)
                    || o.lastInteractionAt().equals(threshold))
        .toList();
  }

  @Override
  public void save(Opportunity opportunity) {
    Objects.requireNonNull(opportunity, "opportunity");
    byId.put(opportunity.id(), opportunity);
  }

  public List<Opportunity> all() {
    return List.copyOf(byId.values());
  }
}
