package com.jobcrm.interfaces.cli.setup;

import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputRenderer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm config ...} command group. Stubs for now (Task 10 wires real config read/write),
 * but the subcommands are real with their parameters declared.
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

  // ---- jobcrm config get <key> ----

  @Component
  @Command(name = "get", description = "Get a configuration value.")
  static class Get implements Runnable {
    @Mixin GlobalOptions options;

    @Parameters(index = "0", paramLabel = "<key>", description = "Configuration key.")
    String key;

    @Override
    public void run() {
      new OutputRenderer().line("config get is not yet implemented (Task 10)");
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
      new OutputRenderer().line("config set is not yet implemented (Task 10)");
    }
  }
}
