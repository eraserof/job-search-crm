package com.jobcrm.core.domain.opportunity;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import java.util.Objects;

/**
 * A reference to a {@code Contact} in the scope of a specific {@code Opportunity}, together with
 * the role the contact plays there. Held inside {@code Opportunity} rather than as a bare {@code
 * ContactId} so that the role-in-this-opportunity is co-located with the reference.
 */
public record ContactRef(ContactId contactId, ContactRole roleInThisOpp) {

  public ContactRef {
    Objects.requireNonNull(contactId, "contactId");
    Objects.requireNonNull(roleInThisOpp, "roleInThisOpp");
  }
}
