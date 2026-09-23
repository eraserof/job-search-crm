package com.jobcrm.agentic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TokenUsageTest {

  @Test
  void zero_constant_is_the_identity_for_add() {
    var u = new TokenUsage(100, 200);
    assertThat(u.add(TokenUsage.ZERO)).isEqualTo(u);
    assertThat(TokenUsage.ZERO.add(u)).isEqualTo(u);
  }

  @Test
  void add_is_component_wise() {
    var a = new TokenUsage(10, 20);
    var b = new TokenUsage(3, 4);
    assertThat(a.add(b)).isEqualTo(new TokenUsage(13, 24));
  }

  @Test
  void total_is_the_sum_of_input_and_output() {
    assertThat(new TokenUsage(7, 5).total()).isEqualTo(12L);
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 0", "0, -1", "-5, -5",
  })
  void negative_tokens_are_rejected(long input, long output) {
    assertThatThrownBy(() -> new TokenUsage(input, output))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
