package com.jobcrm.interfaces.cli;

import picocli.CommandLine.Option;

/**
 * Options shared by every command, pulled in with {@code @Mixin}. Keeps the global flags (ui-cli.md
 * § Global Flags) defined once.
 */
public class GlobalOptions {

  @Option(names = "--json", description = "Emit machine-readable JSON instead of a text table.")
  boolean json;

  @Option(
      names = {"--quiet", "-q"},
      description = "Suppress non-essential output.")
  boolean quiet;

  @Option(
      names = {"--verbose", "-v"},
      description = "Verbose/debug output.")
  boolean verbose;

  @Option(
      names = "--config",
      paramLabel = "<path>",
      description = "Override the config-file location.")
  String configPath;

  public OutputFormat format() {
    return json ? OutputFormat.JSON : OutputFormat.TABLE;
  }

  public boolean quiet() {
    return quiet;
  }

  public boolean verbose() {
    return verbose;
  }
}
