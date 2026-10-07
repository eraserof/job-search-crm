package com.jobcrm.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import com.jobcrm.core.domain.interaction.Interaction;
import com.jobcrm.core.domain.interaction.InteractionRepository;
import com.jobcrm.core.domain.opportunity.IllegalStageTransition;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.opportunity.Stage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class OpportunityServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final CompanyId COMPANY = CompanyId.newId();

  private OpportunityRepository opportunities;
  private InteractionRepository interactions;
  private OpportunityService service;

  @BeforeEach
  void setUp() {
    opportunities = Mockito.mock(OpportunityRepository.class);
    interactions = Mockito.mock(InteractionRepository.class);
    service = new OpportunityService(opportunities, interactions, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void create_persists_and_returns_id() {
    OpportunityId id = service.create(COMPANY, "Backend Engineer");
    verify(opportunities).save(any(Opportunity.class));
    assertThat(id).isNotNull();
  }

  @Test
  void advanceStage_illegal_transition_throws_and_does_not_save() {
    Opportunity opp = Opportunity.create(COMPANY, "Backend", NOW); // APPLIED
    when(opportunities.findById(opp.id())).thenReturn(Optional.of(opp));

    // APPLIED -> OFFER is illegal.
    assertThatThrownBy(() -> service.advanceStage(opp.id(), Stage.OFFER))
        .isInstanceOf(IllegalStageTransition.class);

    verify(opportunities, never()).save(any());
    assertThat(opp.stage()).isEqualTo(Stage.APPLIED);
  }

  @Test
  void advanceStage_legal_transition_saves() {
    Opportunity opp = Opportunity.create(COMPANY, "Backend", NOW);
    when(opportunities.findById(opp.id())).thenReturn(Optional.of(opp));

    service.advanceStage(opp.id(), Stage.RECRUITER_SCREEN);

    verify(opportunities).save(opp);
    assertThat(opp.stage()).isEqualTo(Stage.RECRUITER_SCREEN);
  }

  @Test
  void advanceStage_unknown_opportunity_throws() {
    OpportunityId missing = OpportunityId.newId();
    when(opportunities.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.advanceStage(missing, Stage.RECRUITER_SCREEN))
        .isInstanceOf(UnknownAggregate.class);
  }

  @Test
  void recordInteraction_writes_interaction_before_opportunity() {
    ContactId c1 = ContactId.newId();
    Opportunity opp = Opportunity.create(COMPANY, "Backend", NOW);
    opp.attachContact(c1, ContactRole.RECRUITER_INTERNAL, NOW);
    when(opportunities.findById(opp.id())).thenReturn(Optional.of(opp));

    RecordInteractionCommand cmd =
        new RecordInteractionCommand(
            List.of(c1), Channel.EMAIL, Direction.INBOUND, NOW, null, "hello", null, null);

    service.recordInteraction(opp.id(), cmd);

    // Authoritative interaction write must precede the derived opportunity update.
    InOrder order = inOrder(interactions, opportunities);
    order.verify(interactions).save(any(Interaction.class));
    order.verify(opportunities).save(opp);
  }

  @Test
  void recordInteraction_with_unattached_contact_throws_before_any_write() {
    ContactId stranger = ContactId.newId();
    Opportunity opp = Opportunity.create(COMPANY, "Backend", NOW); // no contacts attached
    when(opportunities.findById(opp.id())).thenReturn(Optional.of(opp));

    RecordInteractionCommand cmd =
        new RecordInteractionCommand(
            List.of(stranger), Channel.EMAIL, Direction.INBOUND, NOW, null, "hello", null, null);

    assertThatThrownBy(() -> service.recordInteraction(opp.id(), cmd))
        .isInstanceOf(IllegalArgumentException.class);

    // Validation happens before persistence: nothing is written.
    verify(interactions, never()).save(any());
    verify(opportunities, never()).save(any());
  }
}
