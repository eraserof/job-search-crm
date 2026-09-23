package com.jobcrm.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

  @Test
  @DisplayName("canonical form is trimmed and lower-cased")
  void canonicalises_input() {
    var addr = new EmailAddress("  Jane.Doe@Stripe.COM  ");
    assertThat(addr.canonical()).isEqualTo("jane.doe@stripe.com");
  }

  @Test
  @DisplayName("domain() returns the substring after '@'")
  void domain_accessor_returns_everything_after_the_at() {
    assertThat(new EmailAddress("a@example.co.uk").domain()).isEqualTo("example.co.uk");
  }

  @Test
  @DisplayName("two addresses that differ only in case are equal")
  void case_insensitive_equality() {
    assertThat(new EmailAddress("A@example.com")).isEqualTo(new EmailAddress("a@example.com"));
  }

  @Test
  @DisplayName("null input throws NPE with a helpful message")
  void null_input_throws() {
    assertThatThrownBy(() -> new EmailAddress(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("canonical");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", // empty
        "   ", // whitespace only
        "noatsign.example.com", // no '@'
        "@example.com", // empty local part
        "user@", // empty domain part
        "a@@b.com", // double '@'
        "a@b", // domain has no '.'
        "user name@example.com", // internal whitespace
        "\t@example.com" // whitespace in local
      })
  @DisplayName("malformed inputs are rejected")
  void malformed_inputs_are_rejected(String input) {
    assertThatThrownBy(() -> new EmailAddress(input)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a@b.c",
        "jane.doe@example.com",
        "with+plus@example.org",
        "with_underscore@sub.domain.example",
        "digits123@example.io",
      })
  @DisplayName("well-formed inputs are accepted")
  void well_formed_inputs_are_accepted(String input) {
    assertThat(new EmailAddress(input).canonical()).isEqualTo(input);
  }
}
