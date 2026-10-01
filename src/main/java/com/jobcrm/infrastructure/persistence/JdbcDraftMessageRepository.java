package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableInstant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.messaging.domain.DraftMessage;
import com.jobcrm.messaging.domain.DraftMessageRepository;
import com.jobcrm.messaging.domain.DraftStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JDBC implementation of {@link DraftMessageRepository}. Drafts have no child tables. */
@Repository
public class JdbcDraftMessageRepository implements DraftMessageRepository {

  private final JdbcSupport jdbc;

  JdbcDraftMessageRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DraftMessage> findById(DraftMessageId id) {
    return jdbc.queryOne(
        selectColumns() + " WHERE id = ?",
        ps -> ps.setString(1, uuid(id.value())),
        JdbcDraftMessageRepository::mapRow);
  }

  @Override
  public List<DraftMessage> findPendingReview() {
    return jdbc.queryList(
        selectColumns() + " WHERE status = 'PENDING_REVIEW' ORDER BY created_at",
        JdbcSupport.noParams(),
        JdbcDraftMessageRepository::mapRow);
  }

  @Override
  public List<DraftMessage> findByOpportunity(OpportunityId opportunity) {
    return jdbc.queryList(
        selectColumns() + " WHERE opportunity_id = ? ORDER BY created_at",
        ps -> ps.setString(1, uuid(opportunity.value())),
        JdbcDraftMessageRepository::mapRow);
  }

  @Override
  public void save(DraftMessage draft) {
    jdbc.update(
        "INSERT INTO draft_message (id, opportunity_id, recipient_id, channel, subject, body, "
            + "status, reviewer_note, created_at, reviewed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(id) DO UPDATE SET "
            + "opportunity_id = excluded.opportunity_id, recipient_id = excluded.recipient_id, "
            + "channel = excluded.channel, subject = excluded.subject, body = excluded.body, "
            + "status = excluded.status, reviewer_note = excluded.reviewer_note, "
            + "reviewed_at = excluded.reviewed_at",
        ps -> {
          ps.setString(1, uuid(draft.id().value()));
          ps.setString(2, uuid(draft.opportunity().value()));
          ps.setString(3, uuid(draft.recipient().value()));
          ps.setString(4, draft.channel().name());
          ps.setString(5, draft.subject());
          ps.setString(6, draft.body());
          ps.setString(7, draft.status().name());
          setNullableString(ps, 8, draft.reviewerNote().orElse(null));
          ps.setString(9, instant(draft.createdAt()));
          setNullableString(ps, 10, draft.reviewedAt().map(SqlTypes::instant).orElse(null));
        });
  }

  private static String selectColumns() {
    return "SELECT id, opportunity_id, recipient_id, channel, subject, body, status, "
        + "reviewer_note, created_at, reviewed_at FROM draft_message";
  }

  private static DraftMessage mapRow(ResultSet rs) throws SQLException {
    return DraftMessage.reconstitute(
        new DraftMessageId(uuid(rs.getString("id"))),
        new OpportunityId(uuid(rs.getString("opportunity_id"))),
        new ContactId(uuid(rs.getString("recipient_id"))),
        Channel.valueOf(rs.getString("channel")),
        rs.getString("subject"),
        rs.getString("body"),
        DraftStatus.valueOf(rs.getString("status")),
        instant(rs.getString("created_at")),
        nullableInstant(rs, "reviewed_at"),
        nullableString(rs, "reviewer_note"));
  }
}
