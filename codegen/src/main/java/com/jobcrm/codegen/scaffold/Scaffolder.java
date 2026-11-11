package com.jobcrm.codegen.scaffold;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.Resources;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.job.RunLog;
import com.jobcrm.codegen.llm.ChatClient;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.llm.Reasoning;
import com.jobcrm.codegen.plan.PlanPrompts;
import com.jobcrm.codegen.plan.PlanRequest;
import com.jobcrm.codegen.scaffold.ScaffoldReply.AddTo;
import com.jobcrm.codegen.scaffold.ScaffoldReply.NewFile;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The scaffold stage: the big model designs new types with contract-and-stub methods. The reply is
 * validated, then saved to {@code codegen/drafts/scaffolds/<request>-<timestamp>/} for review. The
 * source tree is never touched here; {@link ScaffoldApplier} copies the reviewed files in.
 *
 * <p>Folder layout: {@code files/<repo-relative path>} (new files), {@code add-to-N.txt} (members
 * for existing classes), {@code MANUAL.txt}, plus {@code prompt.txt} and {@code reply.txt}.
 */
public final class Scaffolder {

  private final Repo repo;
  private final ChatClient chat;
  private final ObjectMapper json;
  private final PrintWriter out;

  public Scaffolder(Repo repo, ChatClient chat, ObjectMapper json, PrintWriter out) {
    this.repo = repo;
    this.chat = chat;
    this.json = json;
    this.out = out;
  }

  /** The draft folder, or why scaffolding failed. */
  public record Result(Path dir, String failure) {
    public boolean ok() {
      return failure == null;
    }
  }

  public Result run(Path requestFile, ResolvedStage stage, int retries) throws LlmException {
    PlanRequest request = readRequest(requestFile);
    String rules = PlanPrompts.rulesHeader(repo.read(repo.codegenDir().resolve("TEMPLATE.txt")));
    String basePrompt =
        ScaffoldPrompts.build(
            Resources.text("scaffold-prompt.txt"),
            rules,
            request.ask(),
            PlanPrompts.contextBlock(repo, request.context()));

    String name = requestFile.getFileName().toString().replaceFirst("\\.json$", "");
    RunLog log = RunLog.create(repo, repo.draftsDir().resolve("scaffolds"), name);
    say(
        "scaffold: model=%s @ %s  (context files: %d)",
        stage.model(), stage.platform().base(), request.context().size());
    log.write("prompt.txt", basePrompt);

    String prompt = basePrompt;
    for (int attempt = 0; attempt <= retries; attempt++) {
      say("attempt %d/%d: calling scaffolder...", attempt + 1, retries + 1);
      String raw = chat.complete(stage, prompt, null);
      String reply = Reasoning.strip(raw);
      log.write(attempt == 0 ? "reply.txt" : "reply-" + attempt + ".txt", raw);

      ScaffoldReply parsed = ScaffoldParser.parse(reply);
      List<String> existing = ScaffoldValidator.existingTargets(parsed, repo);
      if (!existing.isEmpty()) {
        say("scaffold rejected, not retrying (re-asking cannot fix this):");
        existing.forEach(p -> say("  - %s already exists", p));
        return new Result(
            log.dir(),
            "the ask collides with existing files: "
                + String.join(", ", existing)
                + ". Change the ask or the targets. see "
                + log.display());
      }
      List<String> errors = ScaffoldValidator.validate(parsed, repo);
      if (errors.isEmpty()) {
        save(parsed, log);
        report(parsed, log);
        return new Result(log.dir(), null);
      }
      say("scaffold rejected:");
      errors.forEach(e -> say("  - %s", e));
      prompt = ScaffoldPrompts.retry(basePrompt, reply, errors);
    }
    return new Result(log.dir(), "no valid scaffold. see " + log.display());
  }

  private void save(ScaffoldReply reply, RunLog log) {
    for (NewFile file : reply.files()) {
      log.write("files/" + file.path(), file.source());
    }
    int n = 1;
    for (AddTo add : reply.additions()) {
      log.write(
          "add-to-" + n++ + ".txt",
          "TARGET: " + add.path() + " :: " + add.className() + "\n\n" + add.body());
    }
    log.write("MANUAL.txt", String.join("\n", reply.manual().stream().map(m -> "- " + m).toList()));
  }

  private void report(ScaffoldReply reply, RunLog log) {
    int stubs = 0;
    for (NewFile file : reply.files()) {
      int count = ScaffoldValidator.countStubs(file.source());
      stubs += count;
      say("  new file %s  (stubs: %d)", file.path(), count);
    }
    for (AddTo add : reply.additions()) {
      stubs += ScaffoldValidator.countStubs(add.body());
      say("  add to   %s :: %s  (NOT auto-applied, merge by hand)", add.path(), add.className());
    }
    for (String item : reply.manual()) {
      say("  MANUAL: %s", item);
    }
    say(
        "SCAFFOLD OK: %d new file(s), %d stub(s) in %s",
        reply.files().size(), stubs, log.display());
    say("review files/, then run:  codegen scaffold-apply %s", log.display());
  }

  private PlanRequest readRequest(Path file) {
    try {
      return json.readValue(file.toFile(), PlanRequest.class);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }

  private void say(String format, Object... args) {
    out.println(String.format(format, args));
    out.flush();
  }
}
