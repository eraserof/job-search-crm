package com.jobcrm.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.app.Main;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the config overlay contract from development-guidelines.md § Configuration and Secrets:
 *
 * <ul>
 *   <li>a property override wins over the packaged {@code application.yml} default,
 *   <li>missing config falls back to the defaults baked into {@code application.yml} and the {@link
 *       JobcrmConfig} record,
 *   <li>a redacted dump of the bound config never contains a provided secret value.
 * </ul>
 *
 * <p>Bound through the full Spring context ({@code classes = Main.class}) because the binding is
 * what we are actually testing — relaxed binding, {@code @ConfigurationPropertiesScan}, and the
 * record's default-fill compact constructor all participate.
 */
class JobcrmConfigTest {

  @Nested
  @SpringBootTest(classes = Main.class)
  class DefaultsFromPackagedYaml {

    @Autowired JobcrmConfig config;

    @Test
    void falls_back_to_packaged_defaults_when_no_override_present() {
      // From the packaged application.yml jobcrm: block.
      assertThat(config.llm().provider()).isEqualTo("anthropic");
      assertThat(config.llm().defaultModel()).isEqualTo("claude-sonnet-4-5");
      assertThat(config.gmail().pollInterval()).isEqualTo("5m");
      assertThat(config.calendar().pollInterval()).isEqualTo("5m");
      assertThat(config.daemon().briefingTime()).isEqualTo("07:00");
      assertThat(config.daemon().staleSweepTime()).isEqualTo("03:00");
      assertThat(config.daemon().silenceThreshold()).isEqualTo("14d");
    }

    @Test
    void secrets_are_unset_by_default_and_redactor_shows_them_as_unset() {
      assertThat(config.llm().apiKey()).isNull();
      assertThat(config.gmail().clientSecret()).isNull();
      assertThat(config.gmail().refreshToken()).isNull();

      String dump = ConfigRedactor.redact(config);
      assertThat(dump).contains("llm.apiKey=(unset)");
      assertThat(dump).contains("gmail.clientSecret=(unset)");
      assertThat(dump).contains("gmail.refreshToken=(unset)");
    }
  }

  @Nested
  @SpringBootTest(classes = Main.class)
  @TestPropertySource(
      properties = {
        "jobcrm.llm.provider=openai",
        "jobcrm.llm.default-model=gpt-override",
        "jobcrm.llm.api-key=sk-OVERRIDE-SECRET",
        "jobcrm.gmail.poll-interval=10m",
        "jobcrm.daemon.silence-threshold=30d"
      })
  class OverridesWinOverDefaults {

    @Autowired JobcrmConfig config;

    @Test
    void property_override_wins_over_packaged_default() {
      assertThat(config.llm().provider()).isEqualTo("openai");
      assertThat(config.llm().defaultModel()).isEqualTo("gpt-override");
      assertThat(config.gmail().pollInterval()).isEqualTo("10m");
      assertThat(config.daemon().silenceThreshold()).isEqualTo("30d");
    }

    @Test
    void unoverridden_values_keep_their_defaults() {
      // calendar.poll-interval wasn't overridden, so it stays at the packaged default.
      assertThat(config.calendar().pollInterval()).isEqualTo("5m");
      assertThat(config.daemon().briefingTime()).isEqualTo("07:00");
    }

    @Test
    void a_bound_secret_never_appears_in_the_redacted_dump() {
      assertThat(config.llm().apiKey()).isEqualTo("sk-OVERRIDE-SECRET");

      String dump = ConfigRedactor.redact(config);
      assertThat(dump).doesNotContain("sk-OVERRIDE-SECRET");
      assertThat(dump).contains("llm.apiKey=***");
    }
  }
}
