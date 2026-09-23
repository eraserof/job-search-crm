package com.jobcrm.core.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.company.CompanyId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void creates_with_display_name() {
    Contact c = Contact.create("Jane Doe", T0);
    assertThat(c.displayName()).isEqualTo("Jane Doe");
    assertThat(c.emails()).isEmpty();
    assertThat(c.phone()).isEmpty();
    assertThat(c.linkedInUrl()).isEmpty();
    assertThat(c.employer()).isEmpty();
    assertThat(c.defaultRoles()).isEmpty();
  }

  @Test
  void rejects_blank_name() {
    assertThatThrownBy(() -> Contact.create("   ", T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addEmail_is_idempotent_by_canonical_form() {
    Contact c = Contact.create("Jane", T0);
    c.addEmail(new EmailAddress("Jane@example.com"), T0);
    c.addEmail(new EmailAddress("jane@example.com"), T0);
    assertThat(c.emails()).hasSize(1);
    assertThat(c.emails().get(0).canonical()).isEqualTo("jane@example.com");
  }

  @Test
  void emails_is_unmodifiable() {
    Contact c = Contact.create("Jane", T0);
    c.addEmail(new EmailAddress("jane@example.com"), T0);
    assertThatThrownBy(() -> c.emails().add(new EmailAddress("x@example.com")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void defaultRoles_is_unmodifiable() {
    Contact c = Contact.create("Jane", T0);
    c.addDefaultRole(ContactRole.RECRUITER_INTERNAL, T0);
    assertThatThrownBy(() -> c.defaultRoles().add(ContactRole.HIRING_MANAGER))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rename_updates_name_and_timestamp() {
    Contact c = Contact.create("Jane", T0);
    Instant later = T0.plusSeconds(60);
    c.rename("Jane Smith", later);
    assertThat(c.displayName()).isEqualTo("Jane Smith");
    assertThat(c.updatedAt()).isEqualTo(later);
  }

  @Test
  void setEmployer_null_clears_the_employer() {
    Contact c = Contact.create("Jane", T0);
    CompanyId acme = CompanyId.newId();
    c.setEmployer(acme, T0);
    assertThat(c.employer()).contains(acme);
    c.setEmployer(null, T0);
    assertThat(c.employer()).isEmpty();
  }
}
