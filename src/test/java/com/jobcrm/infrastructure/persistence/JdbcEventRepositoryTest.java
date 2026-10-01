package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.event.Event;
import com.jobcrm.core.domain.event.EventId;
import com.jobcrm.core.domain.event.EventKind;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcEventRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private OpportunityId opportunity;
  private ContactId participantA;
  private ContactId participantB;

  private JdbcEventRepository repo() {
    return new JdbcEventRepository(jdbc());
  }

  @BeforeEach
  void seedParents() {
    UUID companyId = UUID.randomUUID();
    opportunity = new OpportunityId(UUID.randomUUID());
    participantA = new ContactId(UUID.randomUUID());
    participantB = new ContactId(UUID.randomUUID());
    insertCompany(companyId, "Acme", T0.toString());
    insertOpportunity(opportunity.value(), companyId, "Engineer", T0.toString());
    insertContact(participantA.value(), "Panelist A", T0.toString());
    insertContact(participantB.value(), "Panelist B", T0.toString());
  }

  @Test
  void savesAndFindsByIdWithParticipants() {
    Instant starts = Instant.parse("2026-03-01T15:00:00Z");
    Instant ends = Instant.parse("2026-03-01T16:00:00Z");
    Event event =
        Event.schedule(
            opportunity,
            EventKind.INTERVIEW,
            starts,
            ends,
            List.of(participantA, participantB),
            "cal-event-1",
            "Technical Interview",
            "Bring laptop");
    repo().save(event);

    Optional<Event> found = repo().findById(event.id());

    assertThat(found).isPresent();
    Event e = found.get();
    assertThat(e.id()).isEqualTo(event.id());
    assertThat(e.opportunity()).isEqualTo(opportunity);
    assertThat(e.kind()).isEqualTo(EventKind.INTERVIEW);
    assertThat(e.startsAt()).isEqualTo(starts);
    assertThat(e.endsAt()).isEqualTo(ends);
    assertThat(e.participants()).containsExactlyInAnyOrder(participantA, participantB);
    assertThat(e.calendarEventId()).contains("cal-event-1");
    assertThat(e.title()).isEqualTo("Technical Interview");
    assertThat(e.notes()).contains("Bring laptop");
  }

  @Test
  void findByOpportunityReturnsAll() {
    repo().save(newEvent("2026-03-01T15:00:00Z", "2026-03-01T16:00:00Z", "c1"));
    repo().save(newEvent("2026-03-02T15:00:00Z", "2026-03-02T16:00:00Z", "c2"));

    assertThat(repo().findByOpportunity(opportunity)).hasSize(2);
  }

  @Test
  void findEndedBetweenIsHalfOpen() {
    repo().save(newEvent("2026-03-01T09:00:00Z", "2026-03-01T10:00:00Z", "early"));
    repo().save(newEvent("2026-03-01T11:00:00Z", "2026-03-01T12:00:00Z", "mid"));
    repo().save(newEvent("2026-03-01T13:00:00Z", "2026-03-01T14:00:00Z", "late"));

    List<Event> found =
        repo()
            .findEndedBetween(
                Instant.parse("2026-03-01T10:00:00Z"), Instant.parse("2026-03-01T14:00:00Z"));

    // [10:00, 14:00): includes the 10:00 and 12:00 ends, excludes the 14:00 end.
    assertThat(found)
        .extracting(Event::calendarEventId)
        .extracting(Optional::get)
        .containsExactlyInAnyOrder("early", "mid");
  }

  @Test
  void findByCalendarEventIdIsIdempotencyLookup() {
    Event event = newEvent("2026-03-01T15:00:00Z", "2026-03-01T16:00:00Z", "cal-xyz");
    repo().save(event);

    Optional<Event> found = repo().findByCalendarEventId("cal-xyz");
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(event.id());
  }

  @Test
  void saveTwiceUpdatesRowAndReplacesParticipants() {
    Event event =
        Event.schedule(
            opportunity,
            EventKind.INTERVIEW,
            Instant.parse("2026-03-01T15:00:00Z"),
            Instant.parse("2026-03-01T16:00:00Z"),
            List.of(participantA, participantB),
            "cal-1",
            "Original Title",
            null);
    repo().save(event);

    Event mutated =
        Event.reconstitute(
            event.id(),
            opportunity,
            EventKind.ONSITE,
            Instant.parse("2026-03-01T15:00:00Z"),
            Instant.parse("2026-03-01T16:00:00Z"),
            List.of(participantA),
            "cal-1",
            "Updated Title",
            null);
    repo().save(mutated);

    Optional<Event> found = repo().findById(event.id());
    assertThat(found).isPresent();
    assertThat(found.get().title()).isEqualTo("Updated Title");
    assertThat(found.get().kind()).isEqualTo(EventKind.ONSITE);
    assertThat(found.get().participants()).containsExactly(participantA);
    assertThat(countRows("event")).isEqualTo(1);
    assertThat(countRows("event_participant")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(EventId.newId())).isEmpty();
  }

  private Event newEvent(String starts, String ends, String calendarEventId) {
    return Event.schedule(
        opportunity,
        EventKind.INTERVIEW,
        Instant.parse(starts),
        Instant.parse(ends),
        List.of(participantA),
        calendarEventId,
        "Interview",
        null);
  }
}
