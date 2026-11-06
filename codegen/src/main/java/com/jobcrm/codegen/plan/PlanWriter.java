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
    ArrayList<Path> result = new ArrayList<>();
    try {
        String planJson = json.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
        repo.write(dir.resolve("plan.json"), planJson);

        int i = 0;
        for (PlanJob job : plan.jobs()) {
            i++;
            String stem = String.format("%02d-%s", i, job.id());
            Path promptFile = dir.resolve(stem + ".prompt.txt");
            repo.write(promptFile, PlanPrompts.codePrompt(rules, job));

            Job j = new Job("code", job.target(), job.signature(), repo.relative(promptFile), job.imports());
            String jobJson = json.writerWithDefaultPrettyPrinter().writeValueAsString(j);
            Path jobFile = dir.resolve(stem + ".job.json");
            repo.write(jobFile, jobJson);

            result.add(jobFile);
        }
    } catch (JsonProcessingException e) {
        throw new UncheckedIOException("cannot write plan", e);
    }
    return result;
  }
}
