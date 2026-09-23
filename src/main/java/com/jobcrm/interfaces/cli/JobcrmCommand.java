package com.jobcrm.interfaces.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * Root {@code jobcrm} command. Subcommands are registered as Spring beans and wired in during later
 * tasks. With no subcommand supplied, prints usage.
 */
@Component
@Command(
    name = "jobcrm",
    mixinStandardHelpOptions = true,
    version = "jobcrm 0.1.0-SNAPSHOT",
    description = "Agentic job-search CRM. Local-only. See `jobcrm <subcommand> --help`.",
    subcommands = {})
public class JobcrmCommand implements Runnable {

  @Override
  public void run() {
    // No subcommand supplied. Picocli will print usage via mixinStandardHelpOptions
    // when --help is passed; here we mirror that for bare invocations.
    new picocli.CommandLine(this).usage(System.out);
  }
}
