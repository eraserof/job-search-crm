package com.jobcrm.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InteractionAndEventTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final OpportunityId OPP = OpportunityId.newId();
  private static final ContactId C1 = ContactId.newId();

  @Test
  void interaction_requires_at_least_one_contact() {
    assertThatThrownBy(
            () ->
                Interaction.record(
                    OPP, List.of(), Channel.EMAIL, Direction.INBOUND, T0, null, "hi", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void interaction_contacts_list_is_unmodifiable() {
    Interaction i =
        Interaction.record(
            OPP, List.of(C1), Channel.EMAIL, Direction.INBOUND, T0, null, "hi", null, null);
    assertThatThrownBy(() -> i.contacts().add(ContactId.newId()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void interaction_normalises_optional_fields_to_empty_when_blank() {
    Interaction i =
        Interaction.record(
            OPP, List.of(C1), Channel.EMAIL, Direction.INBOUND, T0, "   ", "hi", "  ", "\t\t");
    assertThat(i.threadKey()).isEmpty();
    assertThat(i.subject()).isEmpty();
    assertThat(i.externalId()).isEmpty();
  }

  @Test
  void event_rejects_end_before_start() {
    Instant start = T0.plus(Duration.ofHours(2));
    Instant end = T0;
    assertThatThrownBy(
            () ->
                Event.schedule(
                    OPP, EventKind.INTERVIEW, start, end, List.of(C1), null, "Panel", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void event_allows_zero_duration() {
    Event e = Event.schedule(OPP, EventKind.INTERVIEW, T0, T0, List.of(C1), null, "Instant", null);
    assertThat(e.startsAt()).isEqualTo(T0);
    assertThat(e.endsAt()).isEqualTo(T0);
  }

  @Test
  void event_requires_non_blank_title() {
    assertThatThrownBy(
            () ->
                Event.schedule(
                    OPP,
                    EventKind.INTERVIEW,
                    T0,
                    T0.plusSeconds(60),
                    List.of(C1),
                    null,
                    "  ",
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
