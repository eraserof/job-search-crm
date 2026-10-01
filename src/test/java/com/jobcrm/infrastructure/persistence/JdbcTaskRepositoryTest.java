package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.core.domain.task.Task;
import com.jobcrm.core.domain.task.TaskId;
import com.jobcrm.core.domain.task.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcTaskRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private OpportunityId opportunity;

  private JdbcTaskRepository repo() {
    return new JdbcTaskRepository(jdbc());
  }

  @BeforeEach
  void seedParents() {
    UUID companyId = UUID.randomUUID();
    opportunity = new OpportunityId(UUID.randomUUID());
    insertCompany(companyId, "Acme", T0.toString());
    insertOpportunity(opportunity.value(), companyId, "Engineer", T0.toString());
  }

  @Test
  void savesAndFindsByIdWithAllFields() {
    Instant due = Instant.parse("2026-02-01T00:00:00Z");
    Task task = Task.create(opportunity, TaskType.THANK_YOU, due, T0, "send thank-you note");
    DraftMessageId draft = DraftMessageId.newId();
    task.attachDraft(draft, T0);
    repo().save(task);

    Optional<Task> found = repo().findById(task.id());

    assertThat(found).isPresent();
    Task t = found.get();
    assertThat(t.id()).isEqualTo(task.id());
    assertThat(t.opportunity()).isEqualTo(opportunity);
    assertThat(t.type()).isEqualTo(TaskType.THANK_YOU);
    assertThat(t.dueAt()).isEqualTo(due);
    assertThat(t.createdAt()).isEqualTo(T0);
    assertThat(t.status().name()).isEqualTo("OPEN");
    assertThat(t.associatedDraft()).contains(draft);
    assertThat(t.note()).contains("send thank-you note");
  }

  @Test
  void savesTaskWithNoDraftOrNote() {
    Task task =
        Task.create(opportunity, TaskType.NUDGE, Instant.parse("2026-02-01T00:00:00Z"), T0, null);
    repo().save(task);

    Optional<Task> found = repo().findById(task.id());
    assertThat(found).isPresent();
    assertThat(found.get().associatedDraft()).isEmpty();
    assertThat(found.get().note()).isEmpty();
  }

  @Test
  void findOpenByOpportunityExcludesClosedTasks() {
    Task open =
        Task.create(opportunity, TaskType.NUDGE, Instant.parse("2026-02-01T00:00:00Z"), T0, null);
    repo().save(open);

    Task done =
        Task.create(opportunity, TaskType.RESPOND, Instant.parse("2026-02-01T00:00:00Z"), T0, null);
    done.markDone(null, T0.plusSeconds(60));
    repo().save(done);

    List<Task> found = repo().findOpenByOpportunity(opportunity);
    assertThat(found).extracting(Task::id).containsExactly(open.id());
  }

  @Test
  void findDueByReturnsOpenTasksAtOrBeforeThreshold() {
    Task dueEarly =
        Task.create(opportunity, TaskType.NUDGE, Instant.parse("2026-02-01T00:00:00Z"), T0, null);
    Task dueLate =
        Task.create(opportunity, TaskType.NUDGE, Instant.parse("2026-03-01T00:00:00Z"), T0, null);
    repo().save(dueEarly);
    repo().save(dueLate);

    List<Task> found = repo().findDueBy(Instant.parse("2026-02-15T00:00:00Z"));
    assertThat(found).extracting(Task::id).containsExactly(dueEarly.id());
  }

  @Test
  void saveTwiceUpdatesInPlace() {
    Task task =
        Task.create(opportunity, TaskType.NUDGE, Instant.parse("2026-02-01T00:00:00Z"), T0, null);
    repo().save(task);

    task.markDone("completed", T0.plusSeconds(120));
    repo().save(task);

    Optional<Task> found = repo().findById(task.id());
    assertThat(found).isPresent();
    assertThat(found.get().status().name()).isEqualTo("DONE");
    assertThat(found.get().note()).contains("completed");
    assertThat(countRows("task")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(TaskId.newId())).isEmpty();
  }
}
