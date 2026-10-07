package com.jobcrm.interfaces.cli.setup;

import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputFormat;
import com.jobcrm.interfaces.cli.OutputRenderer;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * {@code jobcrm init} — bootstraps a local jobcrm installation. For now this is minimal: config and
 * database are handled by Spring/Flyway at startup. Real config bootstrapping arrives in Task 10.
 */
@Component
@Command(name = "init", description = "Initialize a local jobcrm installation.")
public class InitCommand implements Runnable {

  @Mixin GlobalOptions options;

  @Override
  public void run() {
    OutputRenderer out = new OutputRenderer();
    if (options.format() == OutputFormat.JSON) {
      out.json(Map.of("status", "ok"));
    } else if (!options.quiet()) {
      out.line("Initialized jobcrm (config + database handled by Spring/Flyway at startup).");
    }
  }
}
