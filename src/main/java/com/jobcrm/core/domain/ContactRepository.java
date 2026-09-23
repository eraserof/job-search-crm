package com.jobcrm.core.domain;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Contact} aggregate roots. */
public interface ContactRepository {

  Optional<Contact> findById(ContactId id);

  /**
   * Looks up a contact by canonical email. At most one contact ever holds a given email (enforced
   * by the repository).
   */
  Optional<Contact> findByEmail(EmailAddress email);

  List<Contact> searchByName(String query, int limit);

  List<Contact> findByCompany(CompanyId company);

  void save(Contact contact);
}
