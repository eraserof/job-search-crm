package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReasoningTest {

  @Test
  void removesALeadingBlock() {
    assertThat(Reasoning.strip("  <think>hmm</think>answer")).isEqualTo("answer");
  }

  @Test
  void anUnclosedLeadingBlockLeavesNothing() {
    assertThat(Reasoning.strip("<think>still thinking when the stream ended")).isEmpty();
  }

  @Test
  void tagsThatAreDataAreLeftAlone() {
    String reply = "Use Pattern.compile(\"<think>.*?</think>\", Pattern.DOTALL) to strip them.";

    assertThat(Reasoning.strip(reply)).isEqualTo(reply);
    assertThat(Reasoning.strip("reasoning without an opening tag</think>answer"))
        .isEqualTo("reasoning without an opening tag</think>answer");
  }

  @Test
  void handlesNull() {
    assertThat(Reasoning.strip(null)).isEmpty();
  }
}
