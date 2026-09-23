package com.jobcrm.messaging.domain;

import com.jobcrm.core.domain.DraftMessageId;
import com.jobcrm.core.domain.OpportunityId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link DraftMessageRepository}. */
public final class InMemoryDraftMessageRepository implements DraftMessageRepository {

  private final Map<DraftMessageId, DraftMessage> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<DraftMessage> findById(DraftMessageId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<DraftMessage> findPendingReview() {
    return byId.values().stream()
        .filter(d -> d.status() == DraftStatus.PENDING_REVIEW)
        .sorted(Comparator.comparing(DraftMessage::createdAt))
        .toList();
  }

  @Override
  public List<DraftMessage> findByOpportunity(OpportunityId opportunity) {
    Objects.requireNonNull(opportunity, "opportunity");
    return byId.values().stream().filter(d -> d.opportunity().equals(opportunity)).toList();
  }

  @Override
  public void save(DraftMessage draft) {
    Objects.requireNonNull(draft, "draft");
    byId.put(draft.id(), draft);
  }

  public List<DraftMessage> all() {
    return List.copyOf(byId.values());
  }
}
