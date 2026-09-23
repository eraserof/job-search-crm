package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable child entity of the {@code Opportunity} aggregate: a scheduled event such as an
 * interview or recruiter call. Typically synced from Google Calendar, keyed by {@link
 * #calendarEventId()} for idempotency.
 */
public final class Event {

  private final EventId id;
  private final OpportunityId opportunity;
  private final EventKind kind;
  private final Instant startsAt;
  private final Instant endsAt;
  private final List<ContactId> participants;
  private final /* nullable */ String calendarEventId;
  private final String title;
  private final /* nullable */ String notes;

  private Event(
      EventId id,
      OpportunityId opportunity,
      EventKind kind,
      Instant startsAt,
      Instant endsAt,
      List<ContactId> participants,
      /* nullable */ String calendarEventId,
      String title,
      /* nullable */ String notes) {
    this.id = Objects.requireNonNull(id, "id");
    this.opportunity = Objects.requireNonNull(opportunity, "opportunity");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
    if (endsAt.isBefore(startsAt)) {
      throw new IllegalArgumentException("endsAt must be at or after startsAt");
    }
    this.participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    this.calendarEventId = trimToNull(calendarEventId);
    this.title = requireNonBlank(title, "title");
    this.notes = trimToNull(notes);
  }

  public static Event schedule(
      OpportunityId opportunity,
      EventKind kind,
      Instant startsAt,
      Instant endsAt,
      List<ContactId> participants,
      /* nullable */ String calendarEventId,
      String title,
      /* nullable */ String notes) {
    return new Event(
        EventId.newId(),
        opportunity,
        kind,
        startsAt,
        endsAt,
        participants,
        calendarEventId,
        title,
        notes);
  }

  public static Event reconstitute(
      EventId id,
      OpportunityId opportunity,
      EventKind kind,
      Instant startsAt,
      Instant endsAt,
      List<ContactId> participants,
      /* nullable */ String calendarEventId,
      String title,
      /* nullable */ String notes) {
    return new Event(
        id, opportunity, kind, startsAt, endsAt, participants, calendarEventId, title, notes);
  }

  public EventId id() {
    return id;
  }

  public OpportunityId opportunity() {
    return opportunity;
  }

  public EventKind kind() {
    return kind;
  }

  public Instant startsAt() {
    return startsAt;
  }

  public Instant endsAt() {
    return endsAt;
  }

  public List<ContactId> participants() {
    return Collections.unmodifiableList(participants);
  }

  public Optional<String> calendarEventId() {
    return Optional.ofNullable(calendarEventId);
  }

  public String title() {
    return title;
  }

  public Optional<String> notes() {
    return Optional.ofNullable(notes);
  }

  private static /* nullable */ String trimToNull(/* nullable */ String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String requireNonBlank(String s, String field) {
    Objects.requireNonNull(s, field);
    String trimmed = s.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
