package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.config.Platform;
import com.jobcrm.codegen.llm.LlmException;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Lists every platform's models, or why it is unreachable. */
@Command(name = "models", description = "List the models each platform in models.json serves.")
final class ModelsCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    for (Map.Entry<String, Platform> p : tool.config.platforms().entrySet()) {
      tool.out.printf("[%s] %s %s%n", p.getKey(), p.getValue().kind(), p.getValue().base());
      try {
        for (String model : tool.lister.list(p.getValue())) {
          tool.out.println("    " + model);
        }
      } catch (LlmException e) {
        tool.out.println("    UNREACHABLE: " + e.getMessage());
      }
    }
    tool.out.flush();
    return 0;
  }
}
