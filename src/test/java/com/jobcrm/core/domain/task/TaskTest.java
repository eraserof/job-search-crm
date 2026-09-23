package com.jobcrm.core.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final OpportunityId OPP = OpportunityId.newId();

  @Test
  void creates_open_task_with_due_date() {
    Instant due = T0.plus(Duration.ofDays(1));
    Task t = Task.create(OPP, TaskType.THANK_YOU, due, T0, "note");
    assertThat(t.status()).isEqualTo(TaskStatus.OPEN);
    assertThat(t.type()).isEqualTo(TaskType.THANK_YOU);
    assertThat(t.dueAt()).isEqualTo(due);
    assertThat(t.note()).contains("note");
    assertThat(t.associatedDraft()).isEmpty();
  }

  @Test
  void rejects_dueAt_before_createdAt() {
    Instant past = T0.minus(Duration.ofSeconds(1));
    assertThatThrownBy(() -> Task.create(OPP, TaskType.THANK_YOU, past, T0, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mark_done_transitions_from_open() {
    Task t = Task.create(OPP, TaskType.THANK_YOU, T0.plusSeconds(60), T0, null);
    t.markDone("sent", T0.plusSeconds(10));
    assertThat(t.status()).isEqualTo(TaskStatus.DONE);
    assertThat(t.note()).contains("sent");
  }

  @Test
  void mark_done_is_idempotent() {
    Task t = Task.create(OPP, TaskType.THANK_YOU, T0.plusSeconds(60), T0, null);
    t.markDone(null, T0);
    t.markDone(null, T0.plusSeconds(1));
    assertThat(t.status()).isEqualTo(TaskStatus.DONE);
  }

  @Test
  void mark_done_after_dismiss_throws() {
    Task t = Task.create(OPP, TaskType.THANK_YOU, T0.plusSeconds(60), T0, null);
    t.dismiss(null, T0);
    assertThatThrownBy(() -> t.markDone(null, T0)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void dismiss_transitions_from_open() {
    Task t = Task.create(OPP, TaskType.NUDGE, T0.plusSeconds(60), T0, null);
    t.dismiss("no longer interested", T0);
    assertThat(t.status()).isEqualTo(TaskStatus.DISMISSED);
  }

  @Test
  void attach_draft_records_association() {
    Task t = Task.create(OPP, TaskType.THANK_YOU, T0.plusSeconds(60), T0, null);
    DraftMessageId d = DraftMessageId.newId();
    t.attachDraft(d, T0);
    assertThat(t.associatedDraft()).contains(d);
  }
}
