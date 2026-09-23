package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Event} child entities. */
public interface EventRepository {

  Optional<Event> findById(EventId id);

  List<Event> findByOpportunity(OpportunityId opportunity);

  /**
   * Returns events with {@code endsAt} in the half-open range {@code [from, to)}. Used by the
   * post-interview follow-up rule to find events that just ended.
   */
  List<Event> findEndedBetween(Instant from, Instant to);

  /** Idempotency lookup by Google Calendar event id. */
  Optional<Event> findByCalendarEventId(String calendarEventId);

  void save(Event event);
}
