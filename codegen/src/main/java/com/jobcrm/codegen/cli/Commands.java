package com.jobcrm.codegen.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** The root {@code codegen} command and its subcommands. */
@Command(
    name = "codegen",
    mixinStandardHelpOptions = true,
    description = "Local plan/code pipeline: the big model plans, the small model fills method bodies.",
    subcommands = {ModelsCommand.class, PlanCommand.class, RunCommand.class, JobCommand.class})
public final class Commands implements Runnable {

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  public static CommandLine commandLine() {
    return new CommandLine(new Commands());
  }
}
