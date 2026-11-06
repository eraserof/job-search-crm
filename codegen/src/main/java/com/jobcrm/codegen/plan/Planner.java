package com.jobcrm.codegen.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.Resources;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.job.RunLog;
import com.jobcrm.codegen.llm.ChatClient;
import com.jobcrm.codegen.llm.LlmException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The plan stage: the big model turns one ask into self-contained method jobs. The plan is
 * validated against the real files; if it is rejected, the planner is asked again (fresh prompt,
 * with the errors). Output goes to {@code codegen/drafts/plans/<request>-<timestamp>/}.
 */
public final class Planner {

  private final Repo repo;
  private final ChatClient chat;
  private final ObjectMapper json;
  private final PrintWriter out;

  public Planner(Repo repo, ChatClient chat, ObjectMapper json, PrintWriter out) {
    this.repo = repo;
    this.chat = chat;
    this.json = json;
    this.out = out;
  }

  /** The result: the plan folder and its job files, or why planning failed. */
  public record Result(Path dir, List<Path> jobFiles, String failure) {
    public boolean ok() {
      return failure == null;
    }
  }

  public Result run(Path requestFile, ResolvedStage stage, int retries) throws LlmException {
    PlanRequest request = read(requestFile, PlanRequest.class);
    String context = PlanPrompts.contextBlock(repo, request.context());
    String basePrompt =
        PlanPrompts.planner(Resources.text("planner-prompt.txt"), request.ask(), context);
    JsonNode responseFormat =
        stage.platform().isLmStudio()
            ? PlanPrompts.responseFormat(json, readTree(Resources.text("plan-schema.json")))
            : null;

    String name = requestFile.getFileName().toString().replaceFirst("\\.json$", "");
    RunLog log = RunLog.create(repo, repo.draftsDir().resolve("plans"), name);
    say(
        "plan: model=%s @ %s  (context files: %d)",
        stage.model(), stage.platform().base(), request.context().size());

    String prompt = basePrompt;
    for (int attempt = 0; attempt <= retries; attempt++) {
      say("attempt %d/%d: calling planner...", attempt + 1, retries + 1);
      log.write("attempt-" + attempt + ".prompt.txt", prompt);
      String raw = chat.complete(stage, prompt, responseFormat);
      log.write("attempt-" + attempt + ".out.txt", raw);

      Optional<String> jsonText = PlanPrompts.extractJson(raw);
      List<String> errors;
      Plan plan = null;
      if (jsonText.isEmpty()) {
        errors = List.of("reply contained no JSON object");
      } else {
        try {
          plan = json.readValue(jsonText.get(), Plan.class);
          errors = PlanValidator.validate(plan, repo);
        } catch (JsonProcessingException e) {
          errors = List.of("invalid JSON: " + e.getOriginalMessage());
        }
      }
      if (errors.isEmpty()) {
        String rules =
            PlanPrompts.rulesHeader(repo.read(repo.codegenDir().resolve("TEMPLATE.txt")));
        List<Path> jobs = PlanWriter.write(plan, log.dir(), rules, repo, json);
        for (Path job : jobs) {
          say("  job %s", job.getFileName());
        }
        say("PLAN OK: %d job(s) in %s", jobs.size(), log.display());
        say("review the *.prompt.txt files, then run:  codegen run %s", log.display());
        return new Result(log.dir(), jobs, null);
      }
      say("plan rejected:");
      errors.forEach(e -> say("  - %s", e));
      prompt = PlanPrompts.retry(basePrompt, jsonText.orElse(null), errors);
    }
    return new Result(log.dir(), List.of(), "no valid plan. see " + log.display());
  }

  private <T> T read(Path file, Class<T> type) {
    try {
      return json.readValue(file.toFile(), type);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }

  private JsonNode readTree(String text) {
    try {
      return json.readTree(text);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("bad bundled JSON", e);
    }
  }

  private void say(String format, Object... args) {
    out.println(String.format(format, args));
    out.flush();
  }
}
