package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable child entity of the {@code Opportunity} aggregate: a follow-up work item with a due date.
 * Status transitions {@code OPEN → DONE} and {@code OPEN → DISMISSED} are enforced by the mutation
 * methods; there is no path back to {@code OPEN}.
 */
public final class Task {

  private final TaskId id;
  private final OpportunityId opportunity;
  private final TaskType type;
  private final Instant dueAt;
  private final Instant createdAt;
  private TaskStatus status;
  private /* nullable */ DraftMessageId associatedDraft;
  private /* nullable */ String note;
  private Instant updatedAt;

  private Task(
      TaskId id,
      OpportunityId opportunity,
      TaskType type,
      Instant dueAt,
      Instant createdAt,
      TaskStatus initialStatus,
      /* nullable */ String note) {
    this.id = Objects.requireNonNull(id, "id");
    this.opportunity = Objects.requireNonNull(opportunity, "opportunity");
    this.type = Objects.requireNonNull(type, "type");
    this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    if (dueAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("dueAt must be at or after createdAt");
    }
    this.status = Objects.requireNonNull(initialStatus, "initialStatus");
    this.note = trimToNull(note);
    this.updatedAt = createdAt;
  }

  public static Task create(
      OpportunityId opportunity,
      TaskType type,
      Instant dueAt,
      Instant now,
      /* nullable */ String note) {
    return new Task(TaskId.newId(), opportunity, type, dueAt, now, TaskStatus.OPEN, note);
  }

  public static Task reconstitute(
      TaskId id,
      OpportunityId opportunity,
      TaskType type,
      Instant dueAt,
      Instant createdAt,
      Instant updatedAt,
      TaskStatus status,
      /* nullable */ DraftMessageId associatedDraft,
      /* nullable */ String note) {
    Task t = new Task(id, opportunity, type, dueAt, createdAt, status, note);
    t.associatedDraft = associatedDraft;
    t.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    return t;
  }

  public TaskId id() {
    return id;
  }

  public OpportunityId opportunity() {
    return opportunity;
  }

  public TaskType type() {
    return type;
  }

  public Instant dueAt() {
    return dueAt;
  }

  public TaskStatus status() {
    return status;
  }

  public Optional<DraftMessageId> associatedDraft() {
    return Optional.ofNullable(associatedDraft);
  }

  public Optional<String> note() {
    return Optional.ofNullable(note);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  /** Transitions an OPEN task to DONE. Idempotent when already DONE. */
  public void markDone(/* nullable */ String note, Instant now) {
    if (status == TaskStatus.DONE) {
      return;
    }
    if (status != TaskStatus.OPEN) {
      throw new IllegalStateException(
          "Cannot mark done a task in status " + status + " (must be OPEN)");
    }
    this.status = TaskStatus.DONE;
    if (note != null) {
      this.note = trimToNull(note);
    }
    touch(now);
  }

  /** Transitions an OPEN task to DISMISSED. Idempotent when already DISMISSED. */
  public void dismiss(/* nullable */ String note, Instant now) {
    if (status == TaskStatus.DISMISSED) {
      return;
    }
    if (status != TaskStatus.OPEN) {
      throw new IllegalStateException(
          "Cannot dismiss a task in status " + status + " (must be OPEN)");
    }
    this.status = TaskStatus.DISMISSED;
    if (note != null) {
      this.note = trimToNull(note);
    }
    touch(now);
  }

  /** Associates a draft message with this task. Overwrites any prior association. */
  public void attachDraft(DraftMessageId draft, Instant now) {
    this.associatedDraft = Objects.requireNonNull(draft, "draft");
    touch(now);
  }

  private void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  private static /* nullable */ String trimToNull(/* nullable */ String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
