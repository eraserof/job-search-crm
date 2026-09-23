package com.jobcrm.core.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final OpportunityId OPP = OpportunityId.newId();
  private static final ContactId C1 = ContactId.newId();

  @Test
  void rejects_end_before_start() {
    Instant start = T0.plus(Duration.ofHours(2));
    Instant end = T0;
    assertThatThrownBy(
            () ->
                Event.schedule(
                    OPP, EventKind.INTERVIEW, start, end, List.of(C1), null, "Panel", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allows_zero_duration() {
    Event e = Event.schedule(OPP, EventKind.INTERVIEW, T0, T0, List.of(C1), null, "Instant", null);
    assertThat(e.startsAt()).isEqualTo(T0);
    assertThat(e.endsAt()).isEqualTo(T0);
  }

  @Test
  void requires_non_blank_title() {
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
