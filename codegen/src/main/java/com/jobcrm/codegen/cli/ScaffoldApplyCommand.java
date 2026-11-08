package com.jobcrm.codegen.cli;

import com.jobcrm.codegen.lock.RunLock;
import com.jobcrm.codegen.scaffold.ScaffoldApplier;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Copies a reviewed scaffold draft's new files into the source tree. */
@Command(
    name = "scaffold-apply",
    description = "Write a reviewed scaffold draft's new files into the repo.")
final class ScaffoldApplyCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Draft folder, e.g. codegen/drafts/scaffolds/x-2026...")
  String draft;

  @Override
  public Integer call() {
    Tool tool = Tool.create();
    try (RunLock lock = RunLock.acquire(tool.lockFile(), "scaffold-apply " + draft)) {
      List<String> written = new ScaffoldApplier(tool.repo).apply(tool.repo.resolve(draft));
      written.forEach(path -> tool.out.println("  wrote " + path));
      tool.out.println(
          "APPLIED " + written.size() + " file(s). Add-to blocks and MANUAL.txt are yours.");
      return 0;
    } catch (IllegalArgumentException e) {
      tool.out.println("REFUSED: " + e.getMessage());
      return 2;
    }
  }
}
