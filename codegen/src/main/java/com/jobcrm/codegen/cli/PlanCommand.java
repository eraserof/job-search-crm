package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.lock.RunLock;
import com.jobcrm.codegen.metrics.CallLabel;
import com.jobcrm.codegen.plan.Planner;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Plan stage: turn a request file into a reviewed-before-running plan folder. */
@Command(name = "plan", description = "Turn a request into per-method jobs (plan stage).")
final class PlanCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Request file, e.g. codegen/requests/x.json")
  String request;

  @Option(names = "--retries", description = "Re-asks after a rejected plan (default 1).")
  int retries = 1;

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    try (RunLock lock = RunLock.acquire(tool.lockFile(), "plan " + request)) {
      ResolvedStage stage = tool.config.resolve("plan");
      tool.requireModel(stage);
      Planner.Result result;
      try (CallLabel.Scope scope = tool.label.use(Tool.requestName(request))) {
        result =
            new Planner(tool.repo, tool.chat, tool.json, tool.out)
                .run(tool.repo.resolve(request), stage, retries);
      }
      if (!result.ok()) {
        tool.out.println("FAILED: " + result.failure());
        return 1;
      }
      return 0;
    } catch (LlmException e) {
      tool.out.println("MODEL UNAVAILABLE: " + e.getMessage());
      return 4;
    }
  }
}
