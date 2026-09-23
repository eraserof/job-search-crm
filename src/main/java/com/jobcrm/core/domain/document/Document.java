package com.jobcrm.core.domain.document;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable domain object representing a stored file — typically a resume version, cover letter, or
 * job description — optionally attached to an opportunity. The file content lives on the local
 * filesystem at {@link #storedPath()}; the domain holds only metadata.
 */
public final class Document {

  private final DocumentId id;
  private final /* nullable */ OpportunityId opportunity;
  private final DocumentKind kind;
  private final String filename;
  private final String storedPath;
  private final Instant createdAt;

  private Document(
      DocumentId id,
      /* nullable */ OpportunityId opportunity,
      DocumentKind kind,
      String filename,
      String storedPath,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.opportunity = opportunity;
    this.kind = Objects.requireNonNull(kind, "kind");
    this.filename = requireNonBlank(filename, "filename");
    this.storedPath = requireNonBlank(storedPath, "storedPath");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static Document create(
      /* nullable */ OpportunityId opportunity,
      DocumentKind kind,
      String filename,
      String storedPath,
      Instant now) {
    return new Document(DocumentId.newId(), opportunity, kind, filename, storedPath, now);
  }

  public static Document reconstitute(
      DocumentId id,
      /* nullable */ OpportunityId opportunity,
      DocumentKind kind,
      String filename,
      String storedPath,
      Instant createdAt) {
    return new Document(id, opportunity, kind, filename, storedPath, createdAt);
  }

  public DocumentId id() {
    return id;
  }

  public Optional<OpportunityId> opportunity() {
    return Optional.ofNullable(opportunity);
  }

  public DocumentKind kind() {
    return kind;
  }

  public String filename() {
    return filename;
  }

  public String storedPath() {
    return storedPath;
  }

  public Instant createdAt() {
    return createdAt;
  }

  private static String requireNonBlank(String s, String field) {
    Objects.requireNonNull(s, field);
    String trimmed = s.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
