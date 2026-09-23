package com.jobcrm.messaging.domain;

import com.jobcrm.core.domain.Channel;
import com.jobcrm.core.domain.ContactId;
import com.jobcrm.core.domain.DraftMessageId;
import com.jobcrm.core.domain.OpportunityId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a message draft awaiting user review. The user is always in the loop — agents
 * produce drafts, humans approve, and sending happens outside the app (v1). The state machine below
 * is enforced by the mutation methods; illegal transitions raise {@link InvalidDraftTransition}.
 *
 * <pre>
 *   PENDING_REVIEW → APPROVED → SENT
 *   PENDING_REVIEW → DISCARDED
 * </pre>
 */
public final class DraftMessage {

  private final DraftMessageId id;
  private final OpportunityId opportunity;
  private final ContactId recipient;
  private final Channel channel;
  private final String subject;
  private final String body;
  private final Instant createdAt;
  private DraftStatus status;
  private /* nullable */ Instant reviewedAt;
  private /* nullable */ String reviewerNote;

  private DraftMessage(
      DraftMessageId id,
      OpportunityId opportunity,
      ContactId recipient,
      Channel channel,
      String subject,
      String body,
      Instant createdAt,
      DraftStatus initialStatus) {
    this.id = Objects.requireNonNull(id, "id");
    this.opportunity = Objects.requireNonNull(opportunity, "opportunity");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
    this.channel = Objects.requireNonNull(channel, "channel");
    this.subject = Objects.requireNonNull(subject, "subject");
    this.body = Objects.requireNonNull(body, "body");
    if (body.isBlank()) {
      throw new IllegalArgumentException("body must not be blank");
    }
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.status = Objects.requireNonNull(initialStatus, "initialStatus");
  }

  /** Factory for a brand-new draft in {@link DraftStatus#PENDING_REVIEW}. */
  public static DraftMessage compose(
      OpportunityId opportunity,
      ContactId recipient,
      Channel channel,
      String subject,
      String body,
      Instant now) {
    return new DraftMessage(
        DraftMessageId.newId(),
        opportunity,
        recipient,
        channel,
        subject,
        body,
        now,
        DraftStatus.PENDING_REVIEW);
  }

  /** Factory for reconstitution from persistence. */
  public static DraftMessage reconstitute(
      DraftMessageId id,
      OpportunityId opportunity,
      ContactId recipient,
      Channel channel,
      String subject,
      String body,
      DraftStatus status,
      Instant createdAt,
      /* nullable */ Instant reviewedAt,
      /* nullable */ String reviewerNote) {
    DraftMessage d =
        new DraftMessage(id, opportunity, recipient, channel, subject, body, createdAt, status);
    d.reviewedAt = reviewedAt;
    d.reviewerNote = reviewerNote;
    return d;
  }

  public DraftMessageId id() {
    return id;
  }

  public OpportunityId opportunity() {
    return opportunity;
  }

  public ContactId recipient() {
    return recipient;
  }

  public Channel channel() {
    return channel;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }

  public DraftStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> reviewedAt() {
    return Optional.ofNullable(reviewedAt);
  }

  public Optional<String> reviewerNote() {
    return Optional.ofNullable(reviewerNote);
  }

  /** {@code PENDING_REVIEW → APPROVED}. */
  public void approve(/* nullable */ String note, Instant now) {
    if (status != DraftStatus.PENDING_REVIEW) {
      throw new InvalidDraftTransition(status, "approve");
    }
    this.status = DraftStatus.APPROVED;
    this.reviewerNote = trimToNull(note);
    this.reviewedAt = Objects.requireNonNull(now, "now");
  }

  /** {@code PENDING_REVIEW → DISCARDED}. */
  public void discard(/* nullable */ String note, Instant now) {
    if (status != DraftStatus.PENDING_REVIEW) {
      throw new InvalidDraftTransition(status, "discard");
    }
    this.status = DraftStatus.DISCARDED;
    this.reviewerNote = trimToNull(note);
    this.reviewedAt = Objects.requireNonNull(now, "now");
  }

  /** {@code APPROVED → SENT}. Called after the user manually sends via Gmail (v1). */
  public void markSent() {
    if (status != DraftStatus.APPROVED) {
      throw new InvalidDraftTransition(status, "markSent");
    }
    this.status = DraftStatus.SENT;
  }

  private static /* nullable */ String trimToNull(/* nullable */ String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
