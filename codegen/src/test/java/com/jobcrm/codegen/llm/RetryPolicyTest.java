package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void standardWaitsThirtySixtyThenOneTwentySeconds() {
    RetryPolicy policy = RetryPolicy.standard();

    assertThat(policy.delayAfter(1)).contains(Duration.ofSeconds(30));
    assertThat(policy.delayAfter(2)).contains(Duration.ofSeconds(60));
    assertThat(policy.delayAfter(3)).contains(Duration.ofSeconds(120));
  }

  @Test
  void noDelayOutsideTheList() {
    RetryPolicy policy = RetryPolicy.standard();

    assertThat(policy.delayAfter(0)).isEmpty();
    assertThat(policy.delayAfter(4)).isEmpty();
    assertThat(RetryPolicy.none().delayAfter(1)).isEmpty();
  }
}
