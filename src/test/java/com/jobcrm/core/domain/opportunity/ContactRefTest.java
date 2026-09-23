package com.jobcrm.core.domain.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import org.junit.jupiter.api.Test;

class ContactRefTest {

  @Test
  void carries_contact_id_and_role() {
    var id = ContactId.newId();
    var ref = new ContactRef(id, ContactRole.HIRING_MANAGER);
    assertThat(ref.contactId()).isEqualTo(id);
    assertThat(ref.roleInThisOpp()).isEqualTo(ContactRole.HIRING_MANAGER);
  }

  @Test
  void rejects_null_contact_id() {
    assertThatThrownBy(() -> new ContactRef(null, ContactRole.OTHER))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_role() {
    assertThatThrownBy(() -> new ContactRef(ContactId.newId(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
