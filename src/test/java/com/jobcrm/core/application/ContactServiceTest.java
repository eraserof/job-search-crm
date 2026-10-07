package com.jobcrm.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.contact.EmailAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ContactServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private ContactRepository repo;
  private ContactService service;

  @BeforeEach
  void setUp() {
    repo = Mockito.mock(ContactRepository.class);
    service = new ContactService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createOrMerge_returns_existing_contact_when_email_already_owned() {
    EmailAddress email = new EmailAddress("jane@example.com");
    Contact existing = Contact.create("Jane", NOW);
    when(repo.findByEmail(email)).thenReturn(Optional.of(existing));

    ContactId id = service.createOrMerge("Jane Doe", email);

    // Dedup by canonical email: converge on the existing contact, no new save.
    assertThat(id).isEqualTo(existing.id());
    verify(repo, never()).save(any());
  }

  @Test
  void createOrMerge_creates_new_contact_with_email_when_absent() {
    EmailAddress email = new EmailAddress("new@example.com");
    when(repo.findByEmail(email)).thenReturn(Optional.empty());

    ContactId id = service.createOrMerge("New Person", email);

    ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().id()).isEqualTo(id);
    assertThat(saved.getValue().displayName()).isEqualTo("New Person");
    assertThat(saved.getValue().emails()).containsExactly(email);
  }

  @Test
  void addEmail_unknown_contact_throws_and_does_not_save() {
    ContactId missing = ContactId.newId();
    when(repo.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.addEmail(missing, new EmailAddress("x@example.com")))
        .isInstanceOf(UnknownAggregate.class);
    verify(repo, never()).save(any());
  }

  @Test
  void rename_updates_and_saves() {
    ContactId id = ContactId.newId();
    Contact contact =
        Contact.reconstitute(
            id, "Old", java.util.List.of(), null, null, null, java.util.Set.of(), NOW, NOW);
    when(repo.findById(id)).thenReturn(Optional.of(contact));

    service.rename(id, "New Name");

    ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().displayName()).isEqualTo("New Name");
  }
}
