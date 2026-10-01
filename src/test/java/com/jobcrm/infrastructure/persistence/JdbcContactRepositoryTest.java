package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.contact.EmailAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcContactRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private JdbcContactRepository repo() {
    return new JdbcContactRepository(jdbc());
  }

  @Test
  void savesAndFindsByIdWithAllFieldsAndCollections() {
    CompanyId employer = new CompanyId(UUID.randomUUID());
    insertCompany(employer.value(), "Acme", T0.toString());

    Contact contact = Contact.create("Jane Recruiter", T0);
    contact.addEmail(new EmailAddress("jane@acme.com"), T0);
    contact.addEmail(new EmailAddress("jane.alt@acme.com"), T0);
    contact.setPhone("+1-555-0100", T0);
    contact.setLinkedInUrl("https://linkedin.com/in/jane", T0);
    contact.setEmployer(employer, T0);
    contact.addDefaultRole(ContactRole.RECRUITER_INTERNAL, T0);
    contact.addDefaultRole(ContactRole.HIRING_MANAGER, T0);
    repo().save(contact);

    Optional<Contact> found = repo().findById(contact.id());

    assertThat(found).isPresent();
    Contact c = found.get();
    assertThat(c.id()).isEqualTo(contact.id());
    assertThat(c.displayName()).isEqualTo("Jane Recruiter");
    assertThat(c.phone()).contains("+1-555-0100");
    assertThat(c.linkedInUrl()).contains("https://linkedin.com/in/jane");
    assertThat(c.employer()).contains(employer);
    assertThat(c.emails())
        .containsExactlyInAnyOrder(
            new EmailAddress("jane@acme.com"), new EmailAddress("jane.alt@acme.com"));
    assertThat(c.defaultRoles())
        .containsExactlyInAnyOrder(ContactRole.RECRUITER_INTERNAL, ContactRole.HIRING_MANAGER);
    assertThat(c.createdAt()).isEqualTo(T0);
  }

  @Test
  void savesContactWithNoOptionalFields() {
    Contact contact = Contact.create("Minimal Person", T0);
    repo().save(contact);

    Optional<Contact> found = repo().findById(contact.id());
    assertThat(found).isPresent();
    assertThat(found.get().phone()).isEmpty();
    assertThat(found.get().linkedInUrl()).isEmpty();
    assertThat(found.get().employer()).isEmpty();
    assertThat(found.get().emails()).isEmpty();
    assertThat(found.get().defaultRoles()).isEmpty();
  }

  @Test
  void findByEmailJoinsContactEmail() {
    Contact contact = Contact.create("Bob Hiring", T0);
    contact.addEmail(new EmailAddress("bob@globex.com"), T0);
    repo().save(contact);

    Optional<Contact> found = repo().findByEmail(new EmailAddress("BOB@GLOBEX.COM"));
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(contact.id());
  }

  @Test
  void findByCompanyFiltersByEmployer() {
    CompanyId employer = new CompanyId(UUID.randomUUID());
    insertCompany(employer.value(), "Globex", T0.toString());

    Contact inside = Contact.create("Inside Person", T0);
    inside.setEmployer(employer, T0);
    repo().save(inside);

    Contact outside = Contact.create("Outside Person", T0);
    repo().save(outside);

    List<Contact> found = repo().findByCompany(employer);
    assertThat(found).extracting(Contact::id).containsExactly(inside.id());
  }

  @Test
  void searchByNameUsesLikeAndRespectsLimit() {
    repo().save(Contact.create("Alice Anderson", T0));
    repo().save(Contact.create("Alicia Keys", T0));
    repo().save(Contact.create("Zoe Zed", T0));

    List<Contact> all = repo().searchByName("ali", 10);
    assertThat(all).extracting(Contact::displayName).contains("Alice Anderson", "Alicia Keys");
    assertThat(all).extracting(Contact::displayName).doesNotContain("Zoe Zed");

    List<Contact> limited = repo().searchByName("ali", 1);
    assertThat(limited).hasSize(1);
  }

  @Test
  void saveTwiceUpdatesRowAndReplacesEmails() {
    Contact contact = Contact.create("Changing Person", T0);
    contact.addEmail(new EmailAddress("old@place.com"), T0);
    repo().save(contact);

    contact.rename("Renamed Person", T0.plusSeconds(60));
    contact.addEmail(new EmailAddress("new@place.com"), T0.plusSeconds(60));
    repo().save(contact);

    Optional<Contact> found = repo().findById(contact.id());
    assertThat(found).isPresent();
    assertThat(found.get().displayName()).isEqualTo("Renamed Person");
    assertThat(found.get().emails())
        .containsExactlyInAnyOrder(
            new EmailAddress("old@place.com"), new EmailAddress("new@place.com"));
    assertThat(countRows("contact")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(ContactId.newId())).isEmpty();
  }
}
