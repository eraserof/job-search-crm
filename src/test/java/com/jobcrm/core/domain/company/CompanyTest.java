package com.jobcrm.core.domain.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void creates_with_trimmed_name_and_no_domain() {
    Company c = Company.create("  Stripe  ", T0);
    assertThat(c.name()).isEqualTo("Stripe");
    assertThat(c.domain()).isEmpty();
    assertThat(c.createdAt()).isEqualTo(T0);
    assertThat(c.updatedAt()).isEqualTo(T0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n  \n"})
  void rejects_blank_name(String blank) {
    assertThatThrownBy(() -> Company.create(blank, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null_name() {
    assertThatThrownBy(() -> Company.create(null, T0)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void rename_updates_name_and_updatedAt() {
    Company c = Company.create("Old", T0);
    Instant later = T0.plus(Duration.ofDays(1));
    c.renameTo("New", later);
    assertThat(c.name()).isEqualTo("New");
    assertThat(c.updatedAt()).isEqualTo(later);
  }

  @Test
  void setDomain_normalises_case_and_whitespace() {
    Company c = Company.create("Stripe", T0);
    c.setDomain("  STRIPE.COM  ", T0);
    assertThat(c.domain()).contains("stripe.com");
  }

  @Test
  void setDomain_with_null_clears_the_domain() {
    Company c = Company.create("Stripe", T0);
    c.setDomain("stripe.com", T0);
    c.setDomain(null, T0);
    assertThat(c.domain()).isEmpty();
  }

  @Test
  void empty_domain_string_is_treated_as_absent() {
    Company c = Company.create("Stripe", T0);
    c.setDomain("   ", T0);
    assertThat(c.domain()).isEmpty();
  }
}
