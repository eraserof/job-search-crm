package com.jobcrm.core.domain.contact;

import java.util.Locale;
import java.util.Objects;

/**
 * A canonicalised email address. Storage form is always trimmed and lower-cased so equality is
 * case-insensitive and whitespace-insensitive. Validation is RFC-5321-ish, not a strict RFC parser:
 * we reject the obvious wrong shapes and let the SMTP layer decide the rest.
 *
 * <p>Rules enforced in the compact constructor:
 *
 * <ul>
 *   <li>Non-null.
 *   <li>Non-empty after trimming.
 *   <li>Contains exactly one {@code @}.
 *   <li>Local part is non-empty.
 *   <li>Domain part is non-empty and contains at least one {@code .}.
 *   <li>Contains no internal whitespace.
 * </ul>
 */
public record EmailAddress(String canonical) {

  public EmailAddress {
    Objects.requireNonNull(canonical, "canonical");
    canonical = canonical.trim().toLowerCase(Locale.ROOT);
    validate(canonical);
  }

  /** Returns the substring after the {@code @} separator. */
  public String domain() {
    return canonical.substring(canonical.indexOf('@') + 1);
  }

  private static void validate(String value) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException("email must not be empty");
    }
    if (containsWhitespace(value)) {
      throw new IllegalArgumentException("email must not contain whitespace: '" + value + "'");
    }
    int atIndex = value.indexOf('@');
    if (atIndex < 0) {
      throw new IllegalArgumentException("email must contain '@': '" + value + "'");
    }
    if (value.indexOf('@', atIndex + 1) >= 0) {
      throw new IllegalArgumentException("email must contain exactly one '@': '" + value + "'");
    }
    String local = value.substring(0, atIndex);
    String domain = value.substring(atIndex + 1);
    if (local.isEmpty()) {
      throw new IllegalArgumentException("email local part must not be empty: '" + value + "'");
    }
    if (domain.isEmpty()) {
      throw new IllegalArgumentException("email domain part must not be empty: '" + value + "'");
    }
    if (!domain.contains(".")) {
      throw new IllegalArgumentException("email domain must contain a '.': '" + value + "'");
    }
  }

  private static boolean containsWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
