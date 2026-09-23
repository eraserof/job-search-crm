package com.jobcrm.core.domain.opportunity;

import com.jobcrm.core.domain.contact.ContactId;

/** Thrown when attaching a {@code Contact} to an {@code Opportunity} it is already attached to. */
public final class DuplicateContactAttachment extends RuntimeException {

  private final OpportunityId opportunity;
  private final ContactId contact;

  public DuplicateContactAttachment(OpportunityId opportunity, ContactId contact) {
    super("Contact " + contact + " is already attached to opportunity " + opportunity);
    this.opportunity = opportunity;
    this.contact = contact;
  }

  public OpportunityId opportunity() {
    return opportunity;
  }

  public ContactId contact() {
    return contact;
  }
}
