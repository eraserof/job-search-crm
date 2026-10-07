package com.jobcrm.core.application;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.interaction.Interaction;
import com.jobcrm.core.domain.interaction.InteractionRepository;
import com.jobcrm.core.domain.opportunity.IllegalStageTransition;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.opportunity.Stage;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Application service for the {@link Opportunity} aggregate.
 *
 * <p>Most methods mutate a single aggregate and are atomic via the repository's own write
 * transaction. {@link #recordInteraction} is the one operation that spans two aggregates
 * (Interaction + Opportunity); it deliberately persists the authoritative {@link Interaction}
 * first, then updates the opportunity's derived view.
 *
 * <p><b>Cross-aggregate consistency.</b> The two writes in {@code recordInteraction} are separate
 * transactions, so a crash between them can leave the interaction committed while the opportunity's
 * interaction-id list / {@code lastInteractionAt} lag. This is safe and self-healing in this design
 * because the opportunity reconstitutes both of those fields from the interaction table on every
 * load — so the next {@code findById} recomputes them. If this ever becomes multi-user, the upgrade
 * path is a domain event (InteractionRecorded) delivered via an outbox table written in the same
 * transaction as the interaction. Not needed for a single-user local app.
 */
@Service
public class OpportunityService {

  private final OpportunityRepository opportunities;
  private final InteractionRepository interactions;
  private final Clock clock;

  OpportunityService(
      OpportunityRepository opportunities, InteractionRepository interactions, Clock clock) {
    this.opportunities = opportunities;
    this.interactions = interactions;
    this.clock = clock;
  }

  /** Creates a new opportunity in {@link Stage#APPLIED}. */
  public OpportunityId create(CompanyId companyId, String role) {
    Opportunity opportunity = Opportunity.create(companyId, role, clock.instant());
    opportunities.save(opportunity);
    return opportunity.id();
  }

  /**
   * Advances the opportunity to {@code next}. Propagates {@link IllegalStageTransition} (unchecked)
   * when the transition is not permitted by the stage state machine; the aggregate is left
   * unchanged in that case.
   */
  public void advanceStage(OpportunityId id, Stage next) {
    Opportunity opportunity = require(id);
    opportunity.advanceStage(next, clock.instant()); // throws IllegalStageTransition if illegal
    opportunities.save(opportunity);
  }

  /** Attaches a contact in the given role. Propagates {@code DuplicateContactAttachment}. */
  public void attachContact(OpportunityId id, ContactId contactId, ContactRole role) {
    Opportunity opportunity = require(id);
    opportunity.attachContact(contactId, role, clock.instant());
    opportunities.save(opportunity);
  }

  /**
   * Records an interaction against the opportunity. Writes the authoritative {@link Interaction}
   * first, then updates the opportunity's derived interaction view. See the class note on
   * cross-aggregate consistency.
   *
   * @return the id of the recorded interaction
   */
  public com.jobcrm.core.domain.interaction.InteractionId recordInteraction(
      OpportunityId id, RecordInteractionCommand cmd) {
    Opportunity opportunity = require(id);
    Interaction interaction =
        Interaction.record(
            id,
            cmd.contacts(),
            cmd.channel(),
            cmd.direction(),
            cmd.occurredAt(),
            cmd.threadKey(),
            cmd.bodyText(),
            cmd.subject(),
            cmd.externalId());

    // Validate against the aggregate's invariants BEFORE persisting anything (e.g. every
    // referenced contact must be attached). recordInteraction throws if not — nothing is written.
    opportunity.recordInteraction(interaction, clock.instant());

    // Authoritative write first: the interaction is the fact.
    interactions.save(interaction);
    // Derived view second: safe to lag, recomputed on load.
    opportunities.save(opportunity);
    return interaction.id();
  }

  public Optional<Opportunity> findById(OpportunityId id) {
    return opportunities.findById(id);
  }

  private Opportunity require(OpportunityId id) {
    return opportunities
        .findById(id)
        .orElseThrow(() -> new UnknownAggregate("opportunity", id.value().toString()));
  }
}
