package com.jobcrm.core.domain.interaction;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Interaction} child entities. Provides cross-opportunity read queries;
 * writes are coordinated by the application layer alongside {@code Opportunity} saves.
 */
public interface InteractionRepository {

  Optional<Interaction> findById(InteractionId id);

  List<Interaction> findByOpportunity(OpportunityId opportunity);

  /**
   * Idempotency lookup: given an external identifier (Gmail messageId, LinkedIn message key),
   * returns the interaction previously recorded for it if any.
   */
  Optional<Interaction> findByExternalId(String externalId);

  List<Interaction> findByThreadKey(String threadKey);

  void save(Interaction interaction);
}
