package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link EventRepository}. */
public final class InMemoryEventRepository implements EventRepository {

  private final Map<EventId, Event> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Event> findById(EventId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<Event> findByOpportunity(OpportunityId opportunity) {
    Objects.requireNonNull(opportunity, "opportunity");
    return byId.values().stream().filter(e -> e.opportunity().equals(opportunity)).toList();
  }

  @Override
  public List<Event> findEndedBetween(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    return byId.values().stream()
        .filter(
            e -> (e.endsAt().isAfter(from) || e.endsAt().equals(from)) && e.endsAt().isBefore(to))
        .toList();
  }

  @Override
  public Optional<Event> findByCalendarEventId(String calendarEventId) {
    Objects.requireNonNull(calendarEventId, "calendarEventId");
    return byId.values().stream()
        .filter(e -> e.calendarEventId().map(calendarEventId::equals).orElse(false))
        .findFirst();
  }

  @Override
  public void save(Event event) {
    Objects.requireNonNull(event, "event");
    event
        .calendarEventId()
        .ifPresent(
            cid ->
                byId.values().stream()
                    .filter(
                        other ->
                            !other.id().equals(event.id())
                                && other.calendarEventId().map(cid::equals).orElse(false))
                    .findFirst()
                    .ifPresent(
                        other -> {
                          throw new IllegalStateException(
                              "calendarEventId " + cid + " already used by " + other.id());
                        }));
    byId.put(event.id(), event);
  }

  public List<Event> all() {
    return List.copyOf(byId.values());
  }
}
