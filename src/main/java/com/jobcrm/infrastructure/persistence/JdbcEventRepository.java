package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.event.Event;
import com.jobcrm.core.domain.event.EventId;
import com.jobcrm.core.domain.event.EventKind;
import com.jobcrm.core.domain.event.EventRepository;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link EventRepository}. The event's participants live in the {@code
 * event_participant} child table. {@link #save(Event)} upserts the event row and replaces its
 * participant rows in a single transaction.
 */
@Repository
public class JdbcEventRepository implements EventRepository {

  private final JdbcSupport jdbc;

  JdbcEventRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Event> findById(EventId id) {
    return jdbc.queryOne(
        selectColumns() + " WHERE id = ?", ps -> ps.setString(1, uuid(id.value())), this::mapRow);
  }

  @Override
  public List<Event> findByOpportunity(OpportunityId opportunity) {
    return jdbc.queryList(
        selectColumns() + " WHERE opportunity_id = ? ORDER BY starts_at",
        ps -> ps.setString(1, uuid(opportunity.value())),
        this::mapRow);
  }

  @Override
  public List<Event> findEndedBetween(Instant from, Instant to) {
    return jdbc.queryList(
        selectColumns() + " WHERE ends_at >= ? AND ends_at < ? ORDER BY ends_at",
        ps -> {
          ps.setString(1, instant(from));
          ps.setString(2, instant(to));
        },
        this::mapRow);
  }

  @Override
  public Optional<Event> findByCalendarEventId(String calendarEventId) {
    return jdbc.queryOne(
        selectColumns() + " WHERE calendar_event_id = ?",
        ps -> ps.setString(1, calendarEventId),
        this::mapRow);
  }

  @Override
  public void save(Event event) {
    jdbc.inWriteTransaction(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO event (id, opportunity_id, kind, starts_at, ends_at, "
                      + "calendar_event_id, title, notes, created_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET "
                      + "opportunity_id = excluded.opportunity_id, kind = excluded.kind, "
                      + "starts_at = excluded.starts_at, ends_at = excluded.ends_at, "
                      + "calendar_event_id = excluded.calendar_event_id, title = excluded.title, "
                      + "notes = excluded.notes")) {
            ps.setString(1, uuid(event.id().value()));
            ps.setString(2, uuid(event.opportunity().value()));
            ps.setString(3, event.kind().name());
            ps.setString(4, instant(event.startsAt()));
            ps.setString(5, instant(event.endsAt()));
            setNullableString(ps, 6, event.calendarEventId().orElse(null));
            ps.setString(7, event.title());
            setNullableString(ps, 8, event.notes().orElse(null));
            ps.setString(9, instant(event.startsAt()));
            ps.executeUpdate();
          }

          try (PreparedStatement del =
              c.prepareStatement("DELETE FROM event_participant WHERE event_id = ?")) {
            del.setString(1, uuid(event.id().value()));
            del.executeUpdate();
          }

          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO event_participant (event_id, contact_id) VALUES (?, ?)")) {
            for (ContactId participant : event.participants()) {
              ins.setString(1, uuid(event.id().value()));
              ins.setString(2, uuid(participant.value()));
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  private static String selectColumns() {
    return "SELECT id, opportunity_id, kind, starts_at, ends_at, calendar_event_id, title, notes, "
        + "created_at FROM event";
  }

  private Event mapRow(ResultSet rs) throws SQLException {
    EventId id = new EventId(uuid(rs.getString("id")));
    return Event.reconstitute(
        id,
        new OpportunityId(uuid(rs.getString("opportunity_id"))),
        EventKind.valueOf(rs.getString("kind")),
        instant(rs.getString("starts_at")),
        instant(rs.getString("ends_at")),
        loadParticipants(id),
        nullableString(rs, "calendar_event_id"),
        rs.getString("title"),
        nullableString(rs, "notes"));
  }

  private List<ContactId> loadParticipants(EventId id) {
    return jdbc.queryList(
        "SELECT contact_id FROM event_participant WHERE event_id = ? ORDER BY contact_id",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new ContactId(uuid(rs.getString("contact_id"))));
  }
}
