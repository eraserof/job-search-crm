package com.jobcrm.interfaces.cli;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves a user-supplied "short reference" (the first 8+ characters of a UUID, or a full UUID)
 * against a set of candidate entities. Lets users type {@code jobcrm opp show 9f2e3a12} instead of
 * the full UUID.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>If the input parses as a full UUID, it matches exactly that id.
 *   <li>Otherwise it is treated as a case-insensitive prefix of the canonical UUID string.
 *   <li>Zero matches → {@link NoSuchRef}. More than one match → {@link AmbiguousRef}.
 * </ul>
 */
public final class ShortRef {

  private ShortRef() {}

  /**
   * Resolves {@code ref} to exactly one candidate.
   *
   * @param ref the user input (short prefix or full UUID)
   * @param candidates the entities to search
   * @param idOf extracts the entity's UUID
   * @param <T> entity type
   * @return the single matching entity
   */
  public static <T> T resolve(String ref, List<T> candidates, Function<T, UUID> idOf) {
    String needle = ref.trim().toLowerCase(java.util.Locale.ROOT);
    if (needle.isEmpty()) {
      throw new NoSuchRef(ref);
    }

    List<T> matches =
        candidates.stream()
            .filter(
                c -> idOf.apply(c).toString().toLowerCase(java.util.Locale.ROOT).startsWith(needle))
            .toList();

    if (matches.isEmpty()) {
      throw new NoSuchRef(ref);
    }
    if (matches.size() > 1) {
      throw new AmbiguousRef(ref, matches.stream().map(c -> idOf.apply(c).toString()).toList());
    }
    return matches.get(0);
  }

  /** No candidate matched the reference. */
  public static final class NoSuchRef extends RuntimeException {
    public NoSuchRef(String ref) {
      super("No match for reference '" + ref + "'");
    }
  }

  /** More than one candidate matched the reference prefix. */
  public static final class AmbiguousRef extends RuntimeException {
    public AmbiguousRef(String ref, List<String> matchingIds) {
      super(
          "Reference '"
              + ref
              + "' is ambiguous; matches "
              + matchingIds.size()
              + ": "
              + String.join(", ", matchingIds)
              + ". Use more characters.");
    }
  }
}
