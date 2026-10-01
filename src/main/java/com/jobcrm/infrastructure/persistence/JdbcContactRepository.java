package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.instant;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.nullableUuid;
import static com.jobcrm.infrastructure.persistence.SqlTypes.setNullableString;
import static com.jobcrm.infrastructure.persistence.SqlTypes.uuid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.contact.EmailAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link ContactRepository}. The contact's emails live in the {@code
 * contact_email} child table (one row per address); {@code default_roles} is stored as a JSON array
 * of role names in the {@code contact} row. {@link #save(Contact)} upserts the contact row and
 * replaces its email rows in a single transaction.
 */
@Repository
public class JdbcContactRepository implements ContactRepository {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final JdbcSupport jdbc;

  JdbcContactRepository(JdbcSupport jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Contact> findById(ContactId id) {
    return jdbc.queryOne(
        "SELECT id, display_name, phone, linkedin_url, employer_id, default_roles, "
            + "created_at, updated_at FROM contact WHERE id = ?",
        ps -> ps.setString(1, uuid(id.value())),
        this::mapRow);
  }

  @Override
  public Optional<Contact> findByEmail(EmailAddress email) {
    return jdbc.queryOne(
        "SELECT c.id, c.display_name, c.phone, c.linkedin_url, c.employer_id, c.default_roles, "
            + "c.created_at, c.updated_at FROM contact c "
            + "JOIN contact_email e ON e.contact_id = c.id WHERE e.email = ?",
        ps -> ps.setString(1, email.canonical()),
        this::mapRow);
  }

  @Override
  public List<Contact> searchByName(String query, int limit) {
    return jdbc.queryList(
        "SELECT id, display_name, phone, linkedin_url, employer_id, default_roles, "
            + "created_at, updated_at FROM contact "
            + "WHERE display_name LIKE ? COLLATE NOCASE ORDER BY display_name LIMIT ?",
        ps -> {
          ps.setString(1, "%" + query + "%");
          ps.setInt(2, limit);
        },
        this::mapRow);
  }

  @Override
  public List<Contact> findByCompany(CompanyId company) {
    return jdbc.queryList(
        "SELECT id, display_name, phone, linkedin_url, employer_id, default_roles, "
            + "created_at, updated_at FROM contact WHERE employer_id = ? ORDER BY display_name",
        ps -> ps.setString(1, uuid(company.value())),
        this::mapRow);
  }

  @Override
  public void save(Contact contact) {
    jdbc.inWriteTransaction(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO contact (id, display_name, phone, linkedin_url, employer_id, "
                      + "default_roles, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET "
                      + "display_name = excluded.display_name, phone = excluded.phone, "
                      + "linkedin_url = excluded.linkedin_url, employer_id = excluded.employer_id, "
                      + "default_roles = excluded.default_roles, updated_at = excluded.updated_at")) {
            ps.setString(1, uuid(contact.id().value()));
            ps.setString(2, contact.displayName());
            setNullableString(ps, 3, contact.phone().orElse(null));
            setNullableString(ps, 4, contact.linkedInUrl().orElse(null));
            setNullableString(ps, 5, contact.employer().map(e -> uuid(e.value())).orElse(null));
            ps.setString(6, serializeRoles(contact.defaultRoles()));
            ps.setString(7, instant(contact.createdAt()));
            ps.setString(8, instant(contact.updatedAt()));
            ps.executeUpdate();
          }

          try (PreparedStatement del =
              c.prepareStatement("DELETE FROM contact_email WHERE contact_id = ?")) {
            del.setString(1, uuid(contact.id().value()));
            del.executeUpdate();
          }

          try (PreparedStatement ins =
              c.prepareStatement("INSERT INTO contact_email (contact_id, email) VALUES (?, ?)")) {
            for (EmailAddress email : contact.emails()) {
              ins.setString(1, uuid(contact.id().value()));
              ins.setString(2, email.canonical());
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  private Contact mapRow(ResultSet rs) throws SQLException {
    ContactId id = new ContactId(uuid(rs.getString("id")));
    List<EmailAddress> emails = loadEmails(id);
    CompanyId employer =
        nullableUuid(rs, "employer_id") == null
            ? null
            : new CompanyId(uuid(rs.getString("employer_id")));
    return Contact.reconstitute(
        id,
        rs.getString("display_name"),
        emails,
        nullableString(rs, "phone"),
        nullableString(rs, "linkedin_url"),
        employer,
        deserializeRoles(rs.getString("default_roles")),
        instant(rs.getString("created_at")),
        instant(rs.getString("updated_at")));
  }

  private List<EmailAddress> loadEmails(ContactId id) {
    return jdbc.queryList(
        "SELECT email FROM contact_email WHERE contact_id = ? ORDER BY email",
        ps -> ps.setString(1, uuid(id.value())),
        rs -> new EmailAddress(rs.getString("email")));
  }

  private static String serializeRoles(Set<ContactRole> roles) {
    List<String> names = new ArrayList<>();
    for (ContactRole role : roles) {
      names.add(role.name());
    }
    try {
      return JSON.writeValueAsString(names);
    } catch (Exception e) {
      throw new PersistenceException("failed to serialize default_roles", null);
    }
  }

  private static Set<ContactRole> deserializeRoles(String json) {
    if (json == null || json.isBlank()) {
      return EnumSet.noneOf(ContactRole.class);
    }
    List<String> names;
    try {
      names = JSON.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new PersistenceException("failed to parse default_roles: " + json, null);
    }
    Set<ContactRole> roles = EnumSet.noneOf(ContactRole.class);
    for (String name : names) {
      roles.add(ContactRole.valueOf(name));
    }
    return roles;
  }
}
