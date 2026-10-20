package com.jobcrm.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.infrastructure.config.JobcrmConfig;
import org.junit.jupiter.api.Test;

class LlmClientFactoryTest {

  private LlmClientFactory factory() {
    // apiKey is only stored, never used, during construction — no network call is made here.
    JobcrmConfig config =
        new JobcrmConfig(
            new JobcrmConfig.Llm("anthropic", "sk-test", "claude-sonnet-4-5"), null, null, null);
    AnthropicLlmClient anthropic = new AnthropicLlmClient(new ObjectMapper(), config);
    return new LlmClientFactory(anthropic);
  }

  @Test
  void resolvesAnthropicProvider() {
    assertThat(factory().forProvider("anthropic")).isInstanceOf(AnthropicLlmClient.class);
  }

  @Test
  void throwsOnUnknownProvider() {
    assertThatExceptionOfType(LlmClientException.class)
        .isThrownBy(() -> factory().forProvider("openai"))
        .withMessageContaining("openai");
  }
}
