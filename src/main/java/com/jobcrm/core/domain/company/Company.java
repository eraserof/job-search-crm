package com.jobcrm.core.domain.company;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a company. Simple identity + name + optional domain used to route inbound
 * email to the right opportunity via sender domain lookups.
 */
public final class Company {

  private final CompanyId id;
  private final Instant createdAt;
  private String name;
  private /* nullable */ String domain;
  private Instant updatedAt;

  private Company(CompanyId id, String name, /* nullable */ String domain, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = validateName(name);
    this.domain = normalizeDomain(domain);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = createdAt;
  }

  /** Factory for a brand-new company. */
  public static Company create(String name, Instant now) {
    return new Company(CompanyId.newId(), name, null, now);
  }

  /** Factory for reconstitution (from persistence). */
  public static Company reconstitute(
      CompanyId id,
      String name, /* nullable */
      String domain,
      Instant createdAt,
      Instant updatedAt) {
    Company c = new Company(id, name, domain, createdAt);
    c.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    return c;
  }

  public CompanyId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Optional<String> domain() {
    return Optional.ofNullable(domain);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void renameTo(String newName, Instant now) {
    this.name = validateName(newName);
    touch(now);
  }

  public void setDomain(String newDomain, Instant now) {
    this.domain = normalizeDomain(newDomain);
    touch(now);
  }

  private void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  private static String validateName(String name) {
    Objects.requireNonNull(name, "name");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("company name must not be blank");
    }
    return trimmed;
  }

  private static /* nullable */ String normalizeDomain(/* nullable */ String domain) {
    if (domain == null) {
      return null;
    }
    String trimmed = domain.trim().toLowerCase(Locale.ROOT);
    return trimmed.isEmpty() ? null : trimmed;
  }
}
