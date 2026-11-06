package com.jobcrm.codegen.plan;

import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.java.MethodLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks a plan against the real files before anything is written. */
public final class PlanValidator {

  private PlanValidator() {}

  /**
   * Every problem found, as messages for the planner; an empty list means the plan is valid.
   *
   * <ol>
   *   <li>No jobs: return {@code List.of("plan has no jobs")}.
   *   <li>For each job, in order:
   *       <ul>
   *         <li>An id already seen: "duplicate job id '<id>'". An id that does not match {@code
   *             "[A-Za-z0-9_-]+"} (or is null): "job id '<id>' must be letters, digits, _ or -".
   *         <li>{@code Path path = repo.resolve(target)}; if {@code !Files.isRegularFile(path)}:
   *             "job '<id>': target not found: <target>", then skip to the next job.
   *         <li>{@code int n = MethodLocator.count(repo.read(path), signature)}. Catch
   *             IllegalArgumentException: "job '<id>': not a method signature: <signature>". n
   *             == -1: "job '<id>': <target> does not parse". n != 1: "job '<id>': signature
   *             '<signature>' found <n> times in <target> (must be exactly 1; copy it verbatim,
   *             without annotations)".
   *         <li>Empty behavior: "job '<id>': behavior is empty".
   *       </ul>
   * </ol>
   */
  public static List<String> validate(Plan plan, Repo repo) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
