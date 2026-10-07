package com.jobcrm.interfaces.cli.setup;

import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputFormat;
import com.jobcrm.interfaces.cli.OutputRenderer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * {@code jobcrm auth ...} command group. For now {@code auth status} reports every integration as
 * "not configured"; real auth wiring arrives later.
 */
@Component
@Command(
    name = "auth",
    description = "Manage integration authentication.",
    subcommands = {AuthCommand.Status.class})
public class AuthCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  // ---- jobcrm auth status ----

  @Component
  @Command(name = "status", description = "Show authentication status of integrations.")
  static class Status implements Runnable {
    @Mixin GlobalOptions options;

    private static final List<String> INTEGRATIONS = List.of("Gmail", "LLM", "Calendar");

    @Override
    public void run() {
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(
            INTEGRATIONS.stream()
                .map(
                    name -> {
                      Map<String, Object> m = new LinkedHashMap<>();
                      m.put("integration", name);
                      m.put("status", "not configured");
                      return m;
                    })
                .toList());
      } else {
        out.table(
            List.of("INTEGRATION", "STATUS"),
            INTEGRATIONS.stream().map(name -> List.of(name, "not configured")).toList());
      }
    }
  }
}
