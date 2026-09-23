package com.jobcrm.core.domain.interaction;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable child entity of the {@code Opportunity} aggregate: a single communication touchpoint
 * (email, LinkedIn message, or phone-call note). Idempotency of inbound ingest is enforced at the
 * repository level via {@link #externalId()}.
 */
public final class Interaction {

  private final InteractionId id;
  private final OpportunityId opportunity;
  private final List<ContactId> contacts;
  private final Channel channel;
  private final Direction direction;
  private final Instant occurredAt;
  private final /* nullable */ String threadKey;
  private final String bodyText;
  private final /* nullable */ String subject;
  private final /* nullable */ String externalId;

  private Interaction(
      InteractionId id,
      OpportunityId opportunity,
      List<ContactId> contacts,
      Channel channel,
      Direction direction,
      Instant occurredAt,
      /* nullable */ String threadKey,
      String bodyText,
      /* nullable */ String subject,
      /* nullable */ String externalId) {
    this.id = Objects.requireNonNull(id, "id");
    this.opportunity = Objects.requireNonNull(opportunity, "opportunity");
    this.contacts = List.copyOf(Objects.requireNonNull(contacts, "contacts"));
    if (this.contacts.isEmpty()) {
      throw new IllegalArgumentException("interaction must reference at least one contact");
    }
    this.channel = Objects.requireNonNull(channel, "channel");
    this.direction = Objects.requireNonNull(direction, "direction");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    this.threadKey = trimToNull(threadKey);
    this.bodyText = Objects.requireNonNull(bodyText, "bodyText");
    this.subject = trimToNull(subject);
    this.externalId = trimToNull(externalId);
  }

  public static Interaction record(
      OpportunityId opportunity,
      List<ContactId> contacts,
      Channel channel,
      Direction direction,
      Instant occurredAt,
      /* nullable */ String threadKey,
      String bodyText,
      /* nullable */ String subject,
      /* nullable */ String externalId) {
    return new Interaction(
        InteractionId.newId(),
        opportunity,
        contacts,
        channel,
        direction,
        occurredAt,
        threadKey,
        bodyText,
        subject,
        externalId);
  }

  public static Interaction reconstitute(
      InteractionId id,
      OpportunityId opportunity,
      List<ContactId> contacts,
      Channel channel,
      Direction direction,
      Instant occurredAt,
      /* nullable */ String threadKey,
      String bodyText,
      /* nullable */ String subject,
      /* nullable */ String externalId) {
    return new Interaction(
        id,
        opportunity,
        contacts,
        channel,
        direction,
        occurredAt,
        threadKey,
        bodyText,
        subject,
        externalId);
  }

  public InteractionId id() {
    return id;
  }

  public OpportunityId opportunity() {
    return opportunity;
  }

  public List<ContactId> contacts() {
    return Collections.unmodifiableList(contacts);
  }

  public Channel channel() {
    return channel;
  }

  public Direction direction() {
    return direction;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Optional<String> threadKey() {
    return Optional.ofNullable(threadKey);
  }

  public String bodyText() {
    return bodyText;
  }

  public Optional<String> subject() {
    return Optional.ofNullable(subject);
  }

  public Optional<String> externalId() {
    return Optional.ofNullable(externalId);
  }

  private static /* nullable */ String trimToNull(/* nullable */ String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
