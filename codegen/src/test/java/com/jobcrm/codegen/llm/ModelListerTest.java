package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelListerTest {

  @Test
  void containsMatchesExactIdsAndOllamaLatestTag() {
    List<String> available = List.of("coder-full-gpu-7b:latest", "qwen3.8-27b");

    assertThat(ModelLister.contains(available, "coder-full-gpu-7b")).isTrue();
    assertThat(ModelLister.contains(available, "coder-full-gpu-7b:latest")).isTrue();
    assertThat(ModelLister.contains(available, "qwen3.8-27b")).isTrue();
    assertThat(ModelLister.contains(available, "qwen3.8")).isFalse();
    assertThat(ModelLister.contains(List.of(), "x")).isFalse();
  }
}
