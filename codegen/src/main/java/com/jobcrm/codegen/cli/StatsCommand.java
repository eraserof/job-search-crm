package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.metrics.CallRecord;
import com.jobcrm.codegen.metrics.StatsReport;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Prints a per-stage summary of every model call recorded so far. */
@Command(name = "stats", description = "Summarize recorded model calls per stage.")
final class StatsCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    List<CallRecord> records = tool.metrics.readAll();
    if (records.isEmpty()) {
      tool.out.println("no calls recorded yet (" + tool.repo.relative(tool.metricsFile()) + ")");
      return 0;
    }
    tool.out.print(StatsReport.report(records));
    tool.out.flush();
    return 0;
  }
}
