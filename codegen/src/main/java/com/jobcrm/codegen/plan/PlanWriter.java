package com.jobcrm.codegen.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.job.Job;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes a validated plan as files the code stage runs. */
public final class PlanWriter {

  private PlanWriter() {}

  /**
   * Writes the plan into {@code dir} and returns the job files in run order.
   *
   * <ol>
   *   <li>{@code repo.write(dir.resolve("plan.json"),
   *       json.writerWithDefaultPrettyPrinter().writeValueAsString(plan))}.
   *   <li>For each job, numbered from 1: {@code String stem = String.format("%02d-%s", i,
   *       job.id())}.
   *       <ul>
   *         <li>{@code Path promptFile = dir.resolve(stem + ".prompt.txt")}; write {@code
   *             PlanPrompts.codePrompt(rules, job)} to it.
   *         <li>{@code Job j = new Job("code", job.target(), job.signature(),
   *             repo.relative(promptFile), job.imports())}; write it pretty-printed to {@code
   *             dir.resolve(stem + ".job.json")} and add that path to the result list.
   *       </ul>
   *   <li>Wrap JsonProcessingException in {@code new UncheckedIOException("cannot write plan",
   *       e)}.
   * </ol>
   */
  public static List<Path> write(Plan plan, Path dir, String rules, Repo repo, ObjectMapper json) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
