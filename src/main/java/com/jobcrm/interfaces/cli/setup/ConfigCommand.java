package com.jobcrm.interfaces.cli.setup;

import com.jobcrm.infrastructure.config.ConfigRedactor;
import com.jobcrm.infrastructure.config.JobcrmConfig;
import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputFormat;
import com.jobcrm.interfaces.cli.OutputRenderer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm config ...} command group.
 *
 * <p>{@code get} reads from the bound {@link JobcrmConfig} (the merged view of packaged defaults,
 * the {@code ~/.jobcrm/config.yml} overlay, and {@code JOBCRM_*} env vars). Secret keys are
 * redacted on display so {@code config get} is safe to run anywhere. {@code set} is deferred: safe,
 * atomic editing of the user's YAML overlay is its own focused piece of work.
 */
@Component
@Command(
    name = "config",
    description = "Read and write configuration.",
    subcommands = {ConfigCommand.Get.class, ConfigCommand.Set.class})
public class ConfigCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  // ---- jobcrm config get [<key>] ----

  @Component
  @org.springframework.context.annotation.Scope("prototype")
  @Command(
      name = "get",
      description = "Get a configuration value, or all values if no key is given.")
  static class Get implements Runnable {
    @Mixin GlobalOptions options;
    private final JobcrmConfig config;

    Get(JobcrmConfig config) {
      this.config = config;
    }

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "<key>",
        description = "Dotted key, e.g. llm.provider. Omit to list everything.")
    String key;

    @Override
    public void run() {
      Map<String, String> view = redactedView(config);
      OutputRenderer out = new OutputRenderer();

      if (key == null || key.isBlank()) {
        if (options.format() == OutputFormat.JSON) {
          out.json(view);
        } else {
          view.forEach((k, v) -> out.line(k + "=" + v));
        }
        return;
      }

      String normalized = key.trim();
      if (!view.containsKey(normalized)) {
        throw new picocli.CommandLine.ParameterException(
            new picocli.CommandLine(this), "Unknown config key: " + normalized);
      }
      String value = view.get(normalized);
      if (options.format() == OutputFormat.JSON) {
        out.json(Map.of("key", normalized, "value", value));
      } else {
        out.line(value);
      }
    }
  }

  // ---- jobcrm config set <key> <value> ----

  @Component
  @Command(name = "set", description = "Set a configuration value.")
  static class Set implements Runnable {
    @Mixin GlobalOptions options;

    @Parameters(index = "0", paramLabel = "<key>", description = "Configuration key.")
    String key;

    @Parameters(index = "1", paramLabel = "<value>", description = "Configuration value.")
    String value;

    @Override
    public void run() {
      // Deferred: writing requires atomic, comment-preserving edits to ~/.jobcrm/config.yml.
      // Tracked separately from Task 10 (which covers the read/bind/redact/logging path).
      new OutputRenderer()
          .line(
              "config set is not yet implemented. Edit ~/.jobcrm/config.yml directly, "
                  + "or set JOBCRM_* environment variables, for now.");
    }
  }

  /** Flattened, redacted key→value view of the config for display. Secrets are masked. */
  static Map<String, String> redactedView(JobcrmConfig config) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("llm.provider", nullToUnset(config.llm().provider()));
    m.put("llm.api-key", ConfigRedactor.mask(config.llm().apiKey()));
    m.put("llm.default-model", nullToUnset(config.llm().defaultModel()));
    m.put("gmail.client-id", nullToUnset(config.gmail().clientId()));
    m.put("gmail.client-secret", ConfigRedactor.mask(config.gmail().clientSecret()));
    m.put("gmail.refresh-token", ConfigRedactor.mask(config.gmail().refreshToken()));
    m.put("gmail.poll-interval", nullToUnset(config.gmail().pollInterval()));
    m.put("calendar.poll-interval", nullToUnset(config.calendar().pollInterval()));
    m.put("daemon.briefing-time", nullToUnset(config.daemon().briefingTime()));
    m.put("daemon.stale-sweep-time", nullToUnset(config.daemon().staleSweepTime()));
    m.put("daemon.silence-threshold", nullToUnset(config.daemon().silenceThreshold()));
    return m;
  }

  private static String nullToUnset(String v) {
    return (v == null || v.isEmpty()) ? "(unset)" : v;
  }
}
