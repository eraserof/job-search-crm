package com.jobcrm.codegen;

import com.jobcrm.codegen.cli.Commands;
import picocli.CommandLine;

/**
 * Entry point. Run from anywhere inside the repo:
 *
 * <pre>
 * codegen models
 * codegen stats
 * codegen scaffold codegen/requests/x.json [--retries N]
 * codegen scaffold-apply codegen/drafts/scaffolds/x-20261106-101500
 * codegen plan codegen/requests/x.json
 * codegen run codegen/drafts/plans/x-20261106-101500 [--from N] [--no-auto-commit]
 * codegen job codegen/jobs/x.json [--code-attempts N] [--repair-rounds N] [--no-compile]
 * </pre>
 *
 * Exit codes: 0 ok, 1 failed, 2 refused or bad input, 3 needs clarification, 4 model unavailable, 5
 * commit failed.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    CommandLine cli = Commands.commandLine();
    cli.setExecutionExceptionHandler(
        (e, commandLine, parseResult) -> {
          commandLine.getErr().println("error: " + e.getMessage());
          return 2;
        });
    System.exit(cli.execute(args));
  }
}
