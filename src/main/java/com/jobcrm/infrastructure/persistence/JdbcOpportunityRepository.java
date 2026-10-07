package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableUuid;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.document.DocumentId;
import com.jobcrm.core.domain.event.EventId;
import com.jobcrm.core.domain.interaction.InteractionId;
import com.jobcrm.core.domain.opportunity.ContactRef;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.opportunity.Stage;
import com.jobcrm.core.domain.task.TaskId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link OpportunityRepository}, the most complex repository in the system.
 *
 * <p>The {@code opportunity} table owns only the scalar columns plus the {@code
 * opportunity_contact} child table. The aggregate's lists of interaction/event/task ids are
 * <em>not</em> stored here — those entities persist themselves in their own tables and are loaded
 * back by querying those tables filtered by {@code opportunity_id}. {@code lastInteractionAt} has
 * no column; it is derived as {@code MAX(interaction.occurred_at)} for this opportunity, falling
 * back to {@code created_at} when there are no interactions.
 *
 * <p>{@link #save(Opportunity)} upserts the opportunity row and replaces its {@code
 * opportunity_contact} rows transactionally; it never writes interaction/event/task rows.
 */
@Repository
public class JdbcOpportunityRepository implements OpportunityRepository {

  private final JdbcSupport jdbc;

  JdbcOpportunityRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Opportunity> findById(OpportunityId id) {
    return jdbc.queryOne(
        selectColumns() + " WHERE id = ?", ps -> ps.setString(1, uuid(id.value())), this::mapRow);
  }

  @Override
  public List<Opportunity> findByCompany(CompanyId company) {
    return jdbc.queryList(
        selectColumns() + " WHERE company_id = ? ORDER BY created_at",
        ps -> ps.setString(1, uuid(company.value())),
        this::mapRow);
  }

  @Override
  public List<Opportunity> findByStage(Stage stage) {
    return jdbc.queryList(
        selectColumns() + " WHERE stage = ? ORDER BY created_at",
        ps -> ps.setString(1, stage.name()),
        this::mapRow);
  }

  @Override
  public List<Opportunity> findAll() {
    return jdbc.queryList(
        selectColumns() + " ORDER BY created_at", JdbcSupport.noParams(), this::mapRow);
  }

  @Override
  public List<Opportunity> searchByText(String query, int limit) {
    return jdbc.queryList(
        selectColumns() + " WHERE role LIKE ? COLLATE NOCASE ORDER BY role LIMIT ?",
        ps -> {
          ps.setString(1, "%" + query + "%");
          ps.setInt(2, limit);
        },
        this::mapRow);
  }

  @Override
  public List<Opportunity> findSilentSince(Instant threshold) {
    // Non-terminal stages only (terminal = REJECTED, WITHDRAWN). lastInteractionAt is derived as
    // the greater of created_at and MAX(interaction.occurred_at), so compute it in SQL via a
    // correlated subquery and filter against the threshold.
    return jdbc.queryList(
        selectColumns()
            + " WHERE stage NOT IN ('REJECTED', 'WITHDRAWN') AND "
            + "MAX(created_at, COALESCE("
            + "(SELECT MAX(occurred_at) FROM interaction i WHERE i.opportunity_id = opportunity.id), "
            + "created_at)) <= ? ORDER BY created_at",
        ps -> ps.setString(1, instant(threshold)),
        this::mapRow);
  }

  @Override
  public void save(Opportunity opportunity) {
    jdbc.inWriteTransaction(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO opportunity (id, company_id, role, stage, resume_document_id, "
                      + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET "
                      + "company_id = excluded.company_id, role = excluded.role, "
                      + "stage = excluded.stage, resume_document_id = excluded.resume_document_id, "
                      + "updated_at = excluded.updated_at")) {
            ps.setString(1, uuid(opportunity.id().value()));
            ps.setString(2, uuid(opportunity.companyId().value()));
            ps.setString(3, opportunity.role());
            ps.setString(4, opportunity.stage().name());
            setNullableString(
                ps, 5, opportunity.resumeVersionSent().map(d -> uuid(d.value())).orElse(null));
            ps.setString(6, instant(opportunity.createdAt()));
            ps.setString(7, instant(opportunity.updatedAt()));
            ps.executeUpdate();
          }

          try (PreparedStatement del =
              c.prepareStatement("DELETE FROM opportunity_contact WHERE opportunity_id = ?")) {
            del.setString(1, uuid(opportunity.id().value()));
            del.executeUpdate();
          }

          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO opportunity_contact (opportunity_id, contact_id, role_in_opp) "
                      + "VALUES (?, ?, ?)")) {
            for (ContactRef ref : opportunity.contacts()) {
              ins.setString(1, uuid(opportunity.id().value()));
              ins.setString(2, uuid(ref.contactId().value()));
              ins.setString(3, ref.roleInThisOpp().name());
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  private static String selectColumns() {
    return "SELECT id, company_id, role, stage, resume_document_id, created_at, updated_at "
        + "FROM opportunity";
  }

  private Opportunity mapRow(ResultSet rs) throws SQLException {
    OpportunityId id = new OpportunityId(uuid(rs.getString("id")));
    Instant createdAt = instant(rs.getString("created_at"));
    DocumentId resume =
        nullableUuid(rs, "resume_document_id") == null
            ? null
            : new DocumentId(uuid(rs.getString("resume_document_id")));
    return Opportunity.reconstitute(
        id,
        new CompanyId(uuid(rs.getString("company_id"))),
        rs.getString("role"),
        Stage.valueOf(rs.getString("stage")),
        resume,
        loadContacts(id),
        loadInteractionIds(id),
        loadEventIds(id),
        loadOpenTaskIds(id),
        createdAt,
        instant(rs.getString("updated_at")),
        deriveLastInteractionAt(id, createdAt));
  }

  private List<ContactRef> loadContacts(OpportunityId id) {
    return jdbc.queryList(
        "SELECT contact_id, role_in_opp FROM opportunity_contact WHERE opportunity_id = ? "
            + "ORDER BY contact_id",
        ps -> ps.setString(1, uuid(id.value())),
        rs ->
            new ContactRef(
                new ContactId(uuid(rs.getString("contact_id"))),
                ContactRole.valueOf(rs.getString("role_in_opp"))));
  }

  private List<InteractionId> loadInteractionIds(OpportunityId id) {
    return jdbc.queryList(
        "SELECT id FROM interaction WHERE opportunity_id = ? ORDER BY occurred_at",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new InteractionId(uuid(rs.getString("id"))));
  }

  private List<EventId> loadEventIds(OpportunityId id) {
    return jdbc.queryList(
        "SELECT id FROM event WHERE opportunity_id = ? ORDER BY starts_at",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new EventId(uuid(rs.getString("id"))));
  }

  private List<TaskId> loadOpenTaskIds(OpportunityId id) {
    return jdbc.queryList(
        "SELECT id FROM task WHERE opportunity_id = ? AND status = 'OPEN' ORDER BY due_at",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new TaskId(uuid(rs.getString("id"))));
  }

  private Instant deriveLastInteractionAt(OpportunityId id, Instant createdAt) {
    // The aggregate value is MAX(occurred_at) over this opportunity's interactions, or created_at
    // when there are none. MAX over an empty set yields SQL NULL, so a null here means "no
    // interactions" and we fall back to created_at. queryList (not queryOne) is used because the
    // null row value would trip Optional.of in queryOne.
    List<Instant> maxOccurred =
        jdbc.queryList(
            "SELECT MAX(occurred_at) AS max_occurred FROM interaction WHERE opportunity_id = ?",
            ps -> ps.setString(1, uuid(id.value())),
            rs -> {
              String v = rs.getString("max_occurred");
              return (v == null || rs.wasNull()) ? createdAt : instant(v);
            });
    return maxOccurred.isEmpty() ? createdAt : maxOccurred.get(0);
  }
}
