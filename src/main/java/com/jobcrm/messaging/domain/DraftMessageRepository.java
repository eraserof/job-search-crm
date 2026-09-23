package com.jobcrm.messaging.domain;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link DraftMessage} aggregate roots. */
public interface DraftMessageRepository {

  Optional<DraftMessage> findById(DraftMessageId id);

  /** Returns drafts currently awaiting user review, in creation order. */
  List<DraftMessage> findPendingReview();

  List<DraftMessage> findByOpportunity(OpportunityId opportunity);

  void save(DraftMessage draft);
}
