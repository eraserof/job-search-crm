package com.jobcrm.codegen.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscalationTest {

  @Test
  void nullsBecomeDefaults() {
    assertThat(new Escalation(null, null, null).withDefaults())
        .isEqualTo(new Escalation(2, 0, "prompt"));
  }

  @Test
  void outOfRangeValuesAreClamped() {
    assertThat(new Escalation(0, -3, "prompt").withDefaults())
        .isEqualTo(new Escalation(1, 0, "prompt"));
  }

  @Test
  void validValuesAreKept() {
    assertThat(new Escalation(3, 2, "code").withDefaults()).isEqualTo(new Escalation(3, 2, "code"));
  }
}
