package com.jobcrm.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a single role at a single company through its full lifecycle. Owns the
 * relationship-manager view: which contacts are involved, which interactions have taken place,
 * which events are scheduled, and which follow-up tasks remain open.
 *
 * <p>Invariants enforced here:
 *
 * <ul>
 *   <li>Stage transitions must satisfy {@link Stage#canTransitionTo(Stage)}. Violations raise
 *       {@link IllegalStageTransition}.
 *   <li>A given {@link ContactId} may be attached at most once. Duplicate attachments raise {@link
 *       DuplicateContactAttachment}.
 *   <li>Recorded interactions must reference this opportunity and at least one attached contact.
 *   <li>Scheduled events must reference this opportunity.
 * </ul>
 *
 * <p>Child entities (Interaction, Event, Task) are referenced by id from within the aggregate for
 * cheap membership checks; full entities are stored in their own repositories to allow
 * cross-opportunity queries (e.g., find interactions by thread key).
 */
public final class Opportunity {

  private final OpportunityId id;
  private final CompanyId companyId;
  private final Instant createdAt;
  private String role;
  private Stage stage;
  private /* nullable */ DocumentId resumeVersionSent;
  private final List<ContactRef> contacts = new ArrayList<>();
  private final List<InteractionId> interactions = new ArrayList<>();
  private final List<EventId> events = new ArrayList<>();
  private final List<TaskId> openTasks = new ArrayList<>();
  private Instant updatedAt;
  private Instant lastInteractionAt;

  private Opportunity(
      OpportunityId id, CompanyId companyId, String role, Stage initialStage, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.companyId = Objects.requireNonNull(companyId, "companyId");
    this.role = requireNonBlank(role, "role");
    this.stage = Objects.requireNonNull(initialStage, "initialStage");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = createdAt;
    this.lastInteractionAt = createdAt;
  }

  /** Factory for a brand-new opportunity, starting in {@link Stage#APPLIED}. */
  public static Opportunity create(CompanyId companyId, String role, Instant now) {
    return new Opportunity(OpportunityId.newId(), companyId, role, Stage.APPLIED, now);
  }

  /** Factory for reconstitution from persistence. */
  public static Opportunity reconstitute(
      OpportunityId id,
      CompanyId companyId,
      String role,
      Stage stage,
      /* nullable */ DocumentId resumeVersionSent,
      List<ContactRef> contacts,
      List<InteractionId> interactions,
      List<EventId> events,
      List<TaskId> openTasks,
      Instant createdAt,
      Instant updatedAt,
      Instant lastInteractionAt) {
    Opportunity o = new Opportunity(id, companyId, role, stage, createdAt);
    o.resumeVersionSent = resumeVersionSent;
    o.contacts.addAll(contacts);
    o.interactions.addAll(interactions);
    o.events.addAll(events);
    o.openTasks.addAll(openTasks);
    o.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    o.lastInteractionAt = Objects.requireNonNull(lastInteractionAt, "lastInteractionAt");
    return o;
  }

  public OpportunityId id() {
    return id;
  }

  public CompanyId companyId() {
    return companyId;
  }

  public String role() {
    return role;
  }

  public Stage stage() {
    return stage;
  }

  public Optional<DocumentId> resumeVersionSent() {
    return Optional.ofNullable(resumeVersionSent);
  }

  public List<ContactRef> contacts() {
    return Collections.unmodifiableList(contacts);
  }

  public List<InteractionId> interactions() {
    return Collections.unmodifiableList(interactions);
  }

  public List<EventId> events() {
    return Collections.unmodifiableList(events);
  }

  public List<TaskId> openTasks() {
    return Collections.unmodifiableList(openTasks);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant lastInteractionAt() {
    return lastInteractionAt;
  }

  /**
   * Transitions to {@code next}. Throws {@link IllegalStageTransition} if the transition is not
   * permitted by the state machine defined on {@link Stage}.
   */
  public void advanceStage(Stage next, Instant now) {
    Objects.requireNonNull(next, "next");
    if (!stage.canTransitionTo(next)) {
      throw new IllegalStageTransition(stage, next);
    }
    this.stage = next;
    touch(now);
  }

  /**
   * Attaches a contact with the given per-opportunity role. Throws {@link
   * DuplicateContactAttachment} if the contact is already attached.
   */
  public void attachContact(ContactId contactId, ContactRole role, Instant now) {
    Objects.requireNonNull(contactId, "contactId");
    Objects.requireNonNull(role, "role");
    if (isAttached(contactId)) {
      throw new DuplicateContactAttachment(id, contactId);
    }
    contacts.add(new ContactRef(contactId, role));
    touch(now);
  }

  /**
   * Detaches a contact. Idempotent: no-op if the contact is not currently attached. Returns {@code
   * true} if a detach actually happened.
   */
  public boolean detachContact(ContactId contactId, Instant now) {
    Objects.requireNonNull(contactId, "contactId");
    boolean removed = contacts.removeIf(ref -> ref.contactId().equals(contactId));
    if (removed) {
      touch(now);
    }
    return removed;
  }

  /**
   * Records an interaction. The interaction must belong to this opportunity and reference at least
   * one attached contact. Idempotent by interaction id.
   */
  public void recordInteraction(Interaction interaction, Instant now) {
    Objects.requireNonNull(interaction, "interaction");
    if (!interaction.opportunity().equals(id)) {
      throw new IllegalArgumentException(
          "interaction references a different opportunity: " + interaction.opportunity());
    }
    for (ContactId c : interaction.contacts()) {
      if (!isAttached(c)) {
        throw new IllegalArgumentException(
            "interaction references a contact not attached to this opportunity: " + c);
      }
    }
    if (interactions.contains(interaction.id())) {
      return;
    }
    interactions.add(interaction.id());
    if (interaction.occurredAt().isAfter(lastInteractionAt)) {
      lastInteractionAt = interaction.occurredAt();
    }
    touch(now);
  }

  /** Schedules an event. Idempotent by event id. */
  public void scheduleEvent(Event event, Instant now) {
    Objects.requireNonNull(event, "event");
    if (!event.opportunity().equals(id)) {
      throw new IllegalArgumentException(
          "event references a different opportunity: " + event.opportunity());
    }
    if (events.contains(event.id())) {
      return;
    }
    events.add(event.id());
    touch(now);
  }

  /** Registers an open task. Idempotent by task id. */
  public void createTask(Task task, Instant now) {
    Objects.requireNonNull(task, "task");
    if (!task.opportunity().equals(id)) {
      throw new IllegalArgumentException(
          "task references a different opportunity: " + task.opportunity());
    }
    if (!openTasks.contains(task.id())) {
      openTasks.add(task.id());
      touch(now);
    }
  }

  /** Removes a task from the "open" list; called when the task transitions to a terminal state. */
  public boolean closeTask(TaskId taskId, Instant now) {
    Objects.requireNonNull(taskId, "taskId");
    boolean removed = openTasks.remove(taskId);
    if (removed) {
      touch(now);
    }
    return removed;
  }

  public void setResumeVersionSent(/* nullable */ DocumentId resume, Instant now) {
    this.resumeVersionSent = resume;
    touch(now);
  }

  public void renameRole(String newRole, Instant now) {
    this.role = requireNonBlank(newRole, "role");
    touch(now);
  }

  /**
   * If the opportunity has been silent for at least {@code silentFor} and is not already in a
   * terminal or ghosted stage, transitions to {@link Stage#GHOSTED}. Returns whether the transition
   * happened.
   */
  public boolean markGhostedIfSilent(java.time.Clock clock, Duration silentFor) {
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(silentFor, "silentFor");
    Instant now = clock.instant();
    if (stage == Stage.GHOSTED || stage.isTerminal()) {
      return false;
    }
    if (Duration.between(lastInteractionAt, now).compareTo(silentFor) < 0) {
      return false;
    }
    if (!stage.canTransitionTo(Stage.GHOSTED)) {
      return false;
    }
    this.stage = Stage.GHOSTED;
    touch(now);
    return true;
  }

  private boolean isAttached(ContactId contactId) {
    return contacts.stream().anyMatch(ref -> ref.contactId().equals(contactId));
  }

  private void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  private static String requireNonBlank(String s, String field) {
    Objects.requireNonNull(s, field);
    String trimmed = s.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
