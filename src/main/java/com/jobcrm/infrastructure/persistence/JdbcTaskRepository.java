package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableUuid;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.core.domain.task.Task;
import com.jobcrm.core.domain.task.TaskId;
import com.jobcrm.core.domain.task.TaskRepository;
import com.jobcrm.core.domain.task.TaskStatus;
import com.jobcrm.core.domain.task.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JDBC implementation of {@link TaskRepository}. Tasks have no child tables. */
@Repository
public class JdbcTaskRepository implements TaskRepository {

  private final JdbcSupport jdbc;

  JdbcTaskRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Task> findById(TaskId id) {
    return jdbc.queryOne(
        selectColumns() + " WHERE id = ?",
        ps -> ps.setString(1, uuid(id.value())),
        JdbcTaskRepository::mapRow);
  }

  @Override
  public List<Task> findOpenByOpportunity(OpportunityId opportunity) {
    return jdbc.queryList(
        selectColumns() + " WHERE opportunity_id = ? AND status = 'OPEN' ORDER BY due_at",
        ps -> ps.setString(1, uuid(opportunity.value())),
        JdbcTaskRepository::mapRow);
  }

  @Override
  public List<Task> findDueBy(Instant threshold) {
    return jdbc.queryList(
        selectColumns() + " WHERE status = 'OPEN' AND due_at <= ? ORDER BY due_at",
        ps -> ps.setString(1, instant(threshold)),
        JdbcTaskRepository::mapRow);
  }

  @Override
  public void save(Task task) {
    jdbc.update(
        "INSERT INTO task (id, opportunity_id, type, due_at, status, associated_draft_id, note, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(id) DO UPDATE SET "
            + "opportunity_id = excluded.opportunity_id, type = excluded.type, "
            + "due_at = excluded.due_at, status = excluded.status, "
            + "associated_draft_id = excluded.associated_draft_id, note = excluded.note, "
            + "updated_at = excluded.updated_at",
        ps -> {
          ps.setString(1, uuid(task.id().value()));
          ps.setString(2, uuid(task.opportunity().value()));
          ps.setString(3, task.type().name());
          ps.setString(4, instant(task.dueAt()));
          ps.setString(5, task.status().name());
          setNullableString(ps, 6, task.associatedDraft().map(d -> uuid(d.value())).orElse(null));
          setNullableString(ps, 7, task.note().orElse(null));
          ps.setString(8, instant(task.createdAt()));
          ps.setString(9, instant(task.updatedAt()));
        });
  }

  private static String selectColumns() {
    return "SELECT id, opportunity_id, type, due_at, status, associated_draft_id, note, "
        + "created_at, updated_at FROM task";
  }

  private static Task mapRow(ResultSet rs) throws SQLException {
    DraftMessageId associatedDraft =
        nullableUuid(rs, "associated_draft_id") == null
            ? null
            : new DraftMessageId(uuid(rs.getString("associated_draft_id")));
    return Task.reconstitute(
        new TaskId(uuid(rs.getString("id"))),
        new OpportunityId(uuid(rs.getString("opportunity_id"))),
        TaskType.valueOf(rs.getString("type")),
        instant(rs.getString("due_at")),
        instant(rs.getString("created_at")),
        instant(rs.getString("updated_at")),
        TaskStatus.valueOf(rs.getString("status")),
        associatedDraft,
        nullableString(rs, "note"));
  }
}
