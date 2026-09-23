package com.jobcrm.core.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only in-memory {@link ContactRepository}. Enforces the "one email → one contact" invariant
 * from {@code domain-model.md} § Contact.
 */
public final class InMemoryContactRepository implements ContactRepository {

  private final Map<ContactId, Contact> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Contact> findById(ContactId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<Contact> findByEmail(EmailAddress email) {
    Objects.requireNonNull(email, "email");
    return byId.values().stream().filter(c -> c.emails().contains(email)).findFirst();
  }

  @Override
  public List<Contact> searchByName(String query, int limit) {
    Objects.requireNonNull(query, "query");
    if (limit <= 0) {
      return List.of();
    }
    String q = query.trim().toLowerCase(Locale.ROOT);
    return byId.values().stream()
        .filter(c -> c.displayName().toLowerCase(Locale.ROOT).contains(q))
        .limit(limit)
        .toList();
  }

  @Override
  public List<Contact> findByCompany(CompanyId company) {
    Objects.requireNonNull(company, "company");
    return byId.values().stream()
        .filter(c -> c.employer().map(company::equals).orElse(false))
        .toList();
  }

  @Override
  public void save(Contact contact) {
    Objects.requireNonNull(contact, "contact");
    // Enforce global email uniqueness.
    for (EmailAddress e : contact.emails()) {
      byId.values().stream()
          .filter(other -> !other.id().equals(contact.id()) && other.emails().contains(e))
          .findFirst()
          .ifPresent(
              other -> {
                throw new IllegalStateException(
                    "Email " + e + " is already claimed by contact " + other.id());
              });
    }
    byId.put(contact.id(), contact);
  }

  public List<Contact> all() {
    return List.copyOf(byId.values());
  }
}
