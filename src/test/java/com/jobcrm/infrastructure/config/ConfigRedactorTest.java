package com.jobcrm.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigRedactorTest {

  @Test
  void secrets_are_masked_and_never_appear_verbatim() {
    JobcrmConfig config =
        new JobcrmConfig(
            new JobcrmConfig.Llm("anthropic", "sk-ant-SUPERSECRET", "claude-sonnet-4-5"),
            new JobcrmConfig.Gmail(
                "client-id-123", "GMAIL-CLIENT-SECRET", "REFRESH-TOKEN-XYZ", "5m"),
            new JobcrmConfig.Calendar("5m"),
            new JobcrmConfig.Daemon("07:00", "03:00", "14d"));

    String dump = ConfigRedactor.redact(config);

    // The three secret values must never appear in the dump.
    assertThat(dump).doesNotContain("sk-ant-SUPERSECRET");
    assertThat(dump).doesNotContain("GMAIL-CLIENT-SECRET");
    assertThat(dump).doesNotContain("REFRESH-TOKEN-XYZ");

    // Non-secret values are fine to show.
    assertThat(dump).contains("anthropic");
    assertThat(dump).contains("client-id-123");
    assertThat(dump).contains("claude-sonnet-4-5");

    // Secrets render as a fixed mask, not a partial reveal.
    assertThat(dump).contains("llm.apiKey=***");
    assertThat(dump).contains("gmail.clientSecret=***");
    assertThat(dump).contains("gmail.refreshToken=***");
  }

  @Test
  void unset_secrets_render_as_unset_not_mask() {
    JobcrmConfig config =
        new JobcrmConfig(
            new JobcrmConfig.Llm("anthropic", null, "claude-sonnet-4-5"),
            new JobcrmConfig.Gmail(null, null, null, "5m"),
            new JobcrmConfig.Calendar("5m"),
            new JobcrmConfig.Daemon("07:00", "03:00", "14d"));

    String dump = ConfigRedactor.redact(config);

    assertThat(dump).contains("llm.apiKey=(unset)");
    assertThat(dump).contains("gmail.clientSecret=(unset)");
    assertThat(dump).doesNotContain("***");
  }

  @Test
  void mask_helper_contract() {
    assertThat(ConfigRedactor.mask(null)).isEqualTo("(unset)");
    assertThat(ConfigRedactor.mask("")).isEqualTo("(unset)");
    assertThat(ConfigRedactor.mask("anything")).isEqualTo("***");
  }
}
