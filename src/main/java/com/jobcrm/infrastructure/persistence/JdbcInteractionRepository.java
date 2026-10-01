package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import com.jobcrm.core.domain.interaction.Interaction;
import com.jobcrm.core.domain.interaction.InteractionId;
import com.jobcrm.core.domain.interaction.InteractionRepository;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link InteractionRepository}. The interaction's referenced contacts live
 * in the {@code interaction_contact} child table. {@link #save(Interaction)} upserts the
 * interaction row and replaces its contact rows in a single transaction.
 */
@Repository
public class JdbcInteractionRepository implements InteractionRepository {

  private final JdbcSupport jdbc;

  JdbcInteractionRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Interaction> findById(InteractionId id) {
    return jdbc.queryOne(
        selectColumns() + " WHERE id = ?", ps -> ps.setString(1, uuid(id.value())), this::mapRow);
  }

  @Override
  public List<Interaction> findByOpportunity(OpportunityId opportunity) {
    return jdbc.queryList(
        selectColumns() + " WHERE opportunity_id = ? ORDER BY occurred_at",
        ps -> ps.setString(1, uuid(opportunity.value())),
        this::mapRow);
  }

  @Override
  public Optional<Interaction> findByExternalId(String externalId) {
    return jdbc.queryOne(
        selectColumns() + " WHERE external_id = ?",
        ps -> ps.setString(1, externalId),
        this::mapRow);
  }

  @Override
  public List<Interaction> findByThreadKey(String threadKey) {
    return jdbc.queryList(
        selectColumns() + " WHERE thread_key = ? ORDER BY occurred_at",
        ps -> ps.setString(1, threadKey),
        this::mapRow);
  }

  @Override
  public void save(Interaction interaction) {
    jdbc.inWriteTransaction(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO interaction (id, opportunity_id, channel, direction, occurred_at, "
                      + "thread_key, external_id, subject, body_text, created_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET "
                      + "opportunity_id = excluded.opportunity_id, channel = excluded.channel, "
                      + "direction = excluded.direction, occurred_at = excluded.occurred_at, "
                      + "thread_key = excluded.thread_key, external_id = excluded.external_id, "
                      + "subject = excluded.subject, body_text = excluded.body_text")) {
            ps.setString(1, uuid(interaction.id().value()));
            ps.setString(2, uuid(interaction.opportunity().value()));
            ps.setString(3, interaction.channel().name());
            ps.setString(4, interaction.direction().name());
            ps.setString(5, instant(interaction.occurredAt()));
            setNullableString(ps, 6, interaction.threadKey().orElse(null));
            setNullableString(ps, 7, interaction.externalId().orElse(null));
            setNullableString(ps, 8, interaction.subject().orElse(null));
            ps.setString(9, interaction.bodyText());
            ps.setString(10, instant(interaction.occurredAt()));
            ps.executeUpdate();
          }

          try (PreparedStatement del =
              c.prepareStatement("DELETE FROM interaction_contact WHERE interaction_id = ?")) {
            del.setString(1, uuid(interaction.id().value()));
            del.executeUpdate();
          }

          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO interaction_contact (interaction_id, contact_id) VALUES (?, ?)")) {
            for (ContactId contact : interaction.contacts()) {
              ins.setString(1, uuid(interaction.id().value()));
              ins.setString(2, uuid(contact.value()));
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  private static String selectColumns() {
    return "SELECT id, opportunity_id, channel, direction, occurred_at, thread_key, external_id, "
        + "subject, body_text, created_at FROM interaction";
  }

  private Interaction mapRow(ResultSet rs) throws SQLException {
    InteractionId id = new InteractionId(uuid(rs.getString("id")));
    return Interaction.reconstitute(
        id,
        new OpportunityId(uuid(rs.getString("opportunity_id"))),
        loadContacts(id),
        Channel.valueOf(rs.getString("channel")),
        Direction.valueOf(rs.getString("direction")),
        instant(rs.getString("occurred_at")),
        nullableString(rs, "thread_key"),
        rs.getString("body_text"),
        nullableString(rs, "subject"),
        nullableString(rs, "external_id"));
  }

  private List<ContactId> loadContacts(InteractionId id) {
    return jdbc.queryList(
        "SELECT contact_id FROM interaction_contact WHERE interaction_id = ? ORDER BY contact_id",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new ContactId(uuid(rs.getString("contact_id"))));
  }
}
