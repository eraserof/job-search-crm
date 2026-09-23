package com.jobcrm.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregate root for a person. Deduplicated by canonical email address across the whole system — an
 * {@link EmailAddress} maps to at most one {@code Contact} (enforced by the repository).
 */
public final class Contact {

  private final ContactId id;
  private final Instant createdAt;
  private String displayName;
  private final List<EmailAddress> emails = new ArrayList<>();
  private /* nullable */ String phone;
  private /* nullable */ String linkedInUrl;
  private /* nullable */ CompanyId employer;
  private final EnumSet<ContactRole> defaultRoles = EnumSet.noneOf(ContactRole.class);
  private Instant updatedAt;

  private Contact(ContactId id, String displayName, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.displayName = validateDisplayName(displayName);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = createdAt;
  }

  /** Factory for a brand-new contact. Additional attributes are set via mutation methods. */
  public static Contact create(String displayName, Instant now) {
    return new Contact(ContactId.newId(), displayName, now);
  }

  /** Factory for reconstitution (from persistence). */
  public static Contact reconstitute(
      ContactId id,
      String displayName,
      List<EmailAddress> emails,
      /* nullable */ String phone,
      /* nullable */ String linkedInUrl,
      /* nullable */ CompanyId employer,
      Set<ContactRole> defaultRoles,
      Instant createdAt,
      Instant updatedAt) {
    Contact c = new Contact(id, displayName, createdAt);
    for (EmailAddress e : emails) {
      c.emails.add(Objects.requireNonNull(e, "email"));
    }
    c.phone = normalize(phone);
    c.linkedInUrl = normalize(linkedInUrl);
    c.employer = employer;
    c.defaultRoles.addAll(defaultRoles);
    c.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    return c;
  }

  public ContactId id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public List<EmailAddress> emails() {
    return Collections.unmodifiableList(emails);
  }

  public Optional<String> phone() {
    return Optional.ofNullable(phone);
  }

  public Optional<String> linkedInUrl() {
    return Optional.ofNullable(linkedInUrl);
  }

  public Optional<CompanyId> employer() {
    return Optional.ofNullable(employer);
  }

  public Set<ContactRole> defaultRoles() {
    return Collections.unmodifiableSet(defaultRoles);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void rename(String newName, Instant now) {
    this.displayName = validateDisplayName(newName);
    touch(now);
  }

  /** Adds an email; no-op if the address is already present. */
  public void addEmail(EmailAddress email, Instant now) {
    Objects.requireNonNull(email, "email");
    if (!emails.contains(email)) {
      emails.add(email);
      touch(now);
    }
  }

  public void setEmployer(/* nullable */ CompanyId employer, Instant now) {
    this.employer = employer;
    touch(now);
  }

  public void setPhone(/* nullable */ String phone, Instant now) {
    this.phone = normalize(phone);
    touch(now);
  }

  public void setLinkedInUrl(/* nullable */ String url, Instant now) {
    this.linkedInUrl = normalize(url);
    touch(now);
  }

  public void addDefaultRole(ContactRole role, Instant now) {
    Objects.requireNonNull(role, "role");
    if (defaultRoles.add(role)) {
      touch(now);
    }
  }

  private void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  private static String validateDisplayName(String name) {
    Objects.requireNonNull(name, "displayName");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    return trimmed;
  }

  private static /* nullable */ String normalize(/* nullable */ String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
