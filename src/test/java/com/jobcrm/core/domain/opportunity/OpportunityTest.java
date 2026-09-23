package com.jobcrm.core.domain.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import com.jobcrm.core.domain.interaction.Interaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpportunityTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final CompanyId COMPANY = CompanyId.newId();

  @Test
  void starts_in_applied_stage() {
    Opportunity o = Opportunity.create(COMPANY, "Sr. Backend Engineer", T0);
    assertThat(o.stage()).isEqualTo(Stage.APPLIED);
    assertThat(o.companyId()).isEqualTo(COMPANY);
    assertThat(o.contacts()).isEmpty();
    assertThat(o.interactions()).isEmpty();
    assertThat(o.events()).isEmpty();
    assertThat(o.openTasks()).isEmpty();
  }

  @Test
  void rejects_blank_role() {
    assertThatThrownBy(() -> Opportunity.create(COMPANY, "   ", T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void advance_to_a_legal_stage_updates_stage_and_updatedAt() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    Instant later = T0.plus(Duration.ofDays(1));
    o.advanceStage(Stage.RECRUITER_SCREEN, later);
    assertThat(o.stage()).isEqualTo(Stage.RECRUITER_SCREEN);
    assertThat(o.updatedAt()).isEqualTo(later);
  }

  @Test
  void advance_to_an_illegal_stage_throws_and_leaves_stage_unchanged() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    assertThatThrownBy(() -> o.advanceStage(Stage.OFFER, T0))
        .isInstanceOf(IllegalStageTransition.class);
    assertThat(o.stage()).isEqualTo(Stage.APPLIED);
  }

  @Test
  void attach_contact_appends_a_ref() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId jane = ContactId.newId();
    o.attachContact(jane, ContactRole.RECRUITER_INTERNAL, T0);
    assertThat(o.contacts()).extracting(ContactRef::contactId).containsExactly(jane);
    assertThat(o.contacts())
        .extracting(ContactRef::roleInThisOpp)
        .containsExactly(ContactRole.RECRUITER_INTERNAL);
  }

  @Test
  void attach_same_contact_twice_throws() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId jane = ContactId.newId();
    o.attachContact(jane, ContactRole.RECRUITER_INTERNAL, T0);
    assertThatThrownBy(() -> o.attachContact(jane, ContactRole.HIRING_MANAGER, T0))
        .isInstanceOf(DuplicateContactAttachment.class);
    assertThat(o.contacts()).hasSize(1);
  }

  @Test
  void detach_contact_removes_the_ref() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId jane = ContactId.newId();
    o.attachContact(jane, ContactRole.RECRUITER_INTERNAL, T0);
    boolean removed = o.detachContact(jane, T0.plusSeconds(1));
    assertThat(removed).isTrue();
    assertThat(o.contacts()).isEmpty();
  }

  @Test
  void detach_unknown_contact_is_a_noop() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    boolean removed = o.detachContact(ContactId.newId(), T0);
    assertThat(removed).isFalse();
  }

  @Test
  void record_interaction_from_another_opportunity_throws() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId c1 = ContactId.newId();
    o.attachContact(c1, ContactRole.RECRUITER_INTERNAL, T0);
    Interaction stray =
        Interaction.record(
            OpportunityId.newId(),
            List.of(c1),
            Channel.EMAIL,
            Direction.INBOUND,
            T0,
            null,
            "hello",
            null,
            null);
    assertThatThrownBy(() -> o.recordInteraction(stray, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void record_interaction_referencing_unattached_contact_throws() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    Interaction i =
        Interaction.record(
            o.id(),
            List.of(ContactId.newId()),
            Channel.EMAIL,
            Direction.INBOUND,
            T0,
            null,
            "hello",
            null,
            null);
    assertThatThrownBy(() -> o.recordInteraction(i, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void record_interaction_happy_path_registers_id_and_advances_lastInteractionAt() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId c1 = ContactId.newId();
    o.attachContact(c1, ContactRole.RECRUITER_INTERNAL, T0);
    Instant occurred = T0.plus(Duration.ofDays(3));
    Interaction i =
        Interaction.record(
            o.id(),
            List.of(c1),
            Channel.EMAIL,
            Direction.INBOUND,
            occurred,
            null,
            "hello",
            null,
            null);
    o.recordInteraction(i, occurred);
    assertThat(o.interactions()).containsExactly(i.id());
    assertThat(o.lastInteractionAt()).isEqualTo(occurred);
  }

  @Test
  void record_interaction_twice_is_idempotent_by_id() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    ContactId c1 = ContactId.newId();
    o.attachContact(c1, ContactRole.RECRUITER_INTERNAL, T0);
    Interaction i =
        Interaction.record(
            o.id(), List.of(c1), Channel.EMAIL, Direction.INBOUND, T0, null, "hello", null, null);
    o.recordInteraction(i, T0);
    o.recordInteraction(i, T0);
    assertThat(o.interactions()).hasSize(1);
  }

  @Test
  void mark_ghosted_if_silent_transitions_when_silent_beyond_threshold() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    o.advanceStage(Stage.RECRUITER_SCREEN, T0);
    Instant now = T0.plus(Duration.ofDays(20));
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    boolean transitioned = o.markGhostedIfSilent(clock, Duration.ofDays(14));
    assertThat(transitioned).isTrue();
    assertThat(o.stage()).isEqualTo(Stage.GHOSTED);
  }

  @Test
  void mark_ghosted_if_silent_is_noop_when_not_silent_enough() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    o.advanceStage(Stage.RECRUITER_SCREEN, T0);
    Instant now = T0.plus(Duration.ofDays(5));
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    boolean transitioned = o.markGhostedIfSilent(clock, Duration.ofDays(14));
    assertThat(transitioned).isFalse();
    assertThat(o.stage()).isEqualTo(Stage.RECRUITER_SCREEN);
  }

  @Test
  void mark_ghosted_if_silent_is_noop_for_terminal_stages() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    o.advanceStage(Stage.REJECTED, T0);
    Instant now = T0.plus(Duration.ofDays(200));
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    assertThat(o.markGhostedIfSilent(clock, Duration.ofDays(14))).isFalse();
    assertThat(o.stage()).isEqualTo(Stage.REJECTED);
  }

  @Test
  void contacts_list_is_unmodifiable() {
    Opportunity o = Opportunity.create(COMPANY, "Backend", T0);
    assertThatThrownBy(() -> o.contacts().add(new ContactRef(ContactId.newId(), ContactRole.OTHER)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
