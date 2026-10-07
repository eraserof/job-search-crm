package com.jobcrm.core.application;

import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.contact.EmailAddress;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Application service for the {@link Contact} aggregate. Single-aggregate transactional facade over
 * {@link ContactRepository}.
 */
@Service
public class ContactService {

  private final ContactRepository contacts;
  private final Clock clock;

  ContactService(ContactRepository contacts, Clock clock) {
    this.contacts = contacts;
    this.clock = clock;
  }

  /**
   * Finds the contact that owns {@code email} and returns its id; if none exists, creates a new
   * contact with the given display name and email. This is the dedup-by-canonical-email entry
   * point: because an email maps to at most one contact, inbound flows that only know a sender
   * address converge on the same contact rather than creating duplicates.
   *
   * <p>When an existing contact is found, its display name is left unchanged (the existing record
   * is authoritative); only a brand-new contact uses {@code displayName}.
   */
  public ContactId createOrMerge(String displayName, EmailAddress email) {
    Objects.requireNonNull(email, "email");
    Optional<Contact> existing = contacts.findByEmail(email);
    if (existing.isPresent()) {
      return existing.get().id();
    }
    Contact contact = Contact.create(displayName, clock.instant());
    contact.addEmail(email, clock.instant());
    contacts.save(contact);
    return contact.id();
  }

  /** Creates a contact with no email yet (e.g. a referral known only by name). */
  public ContactId create(String displayName) {
    Contact contact = Contact.create(displayName, clock.instant());
    contacts.save(contact);
    return contact.id();
  }

  public void rename(ContactId id, String newName) {
    Contact contact = require(id);
    contact.rename(newName, clock.instant());
    contacts.save(contact);
  }

  /**
   * Adds an email to a contact. Idempotent at the aggregate level (adding an address the contact
   * already holds is a no-op). The repository enforces global one-email-to-one-contact uniqueness,
   * so attaching an address already owned by a different contact fails as a {@code
   * PersistenceException} from the unique constraint.
   */
  public void addEmail(ContactId id, EmailAddress email) {
    Contact contact = require(id);
    contact.addEmail(email, clock.instant());
    contacts.save(contact);
  }

  public Optional<Contact> findById(ContactId id) {
    return contacts.findById(id);
  }

  private Contact require(ContactId id) {
    return contacts
        .findById(id)
        .orElseThrow(() -> new UnknownAggregate("contact", id.value().toString()));
  }
}
