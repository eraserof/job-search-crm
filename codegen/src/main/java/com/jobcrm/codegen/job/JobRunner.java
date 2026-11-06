package com.jobcrm.codegen.job;

import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.Resources;
import com.jobcrm.codegen.build.Compiler;
import com.jobcrm.codegen.build.CompilerErrors;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.java.BodyExtractor;
import com.jobcrm.codegen.java.BodySyntax;
import com.jobcrm.codegen.java.MethodLocator;
import com.jobcrm.codegen.java.Splicer;
import com.jobcrm.codegen.llm.ChatClient;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.llm.Reasoning;
import com.jobcrm.codegen.vcs.Vcs;
import com.jobcrm.codegen.vcs.VcsException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one job up the escalation ladder:
 *
 * <ol>
 *   <li>code stage, {@code codeAttempts} times; retries get the errors as a hint, never the failed
 *       code;
 *   <li>repair stage rewrites the job prompt (not the code);
 *   <li>code stage again with the revised prompt; repeat for {@code repairRounds}.
 * </ol>
 *
 * Every attempt is spliced onto the ORIGINAL file, so failures never stack. On any failure,
 * including an exception, the target is restored. A prompt that succeeded after repair replaces the
 * job's prompt file (the first original is kept as {@code *.orig}). With auto-commit, the target
 * alone is committed after it compiles.
 */
public final class JobRunner {

  private final Repo repo;
  private final ChatClient chat;
  private final Compiler compiler;
  private final Vcs vcs;
  private final PrintWriter out;
  private final String repairTemplate;

  public JobRunner(Repo repo, ChatClient chat, Compiler compiler, Vcs vcs, PrintWriter out) {
    this.repo = repo;
    this.chat = chat;
    this.compiler = compiler;
    this.vcs = vcs;
    this.out = out;
    this.repairTemplate = Resources.text("repair-prompt.txt");
  }

  /**
   * @param jobFile the job's file, used for its name and its plan folder
   * @param code the stage that writes code
   * @param repair nullable; required when {@code options.repairRounds() > 0}
   */
  public JobOutcome run(
      Path jobFile, Job job, ResolvedStage code, ResolvedStage repair, JobOptions options) {
    String name = jobName(jobFile);
    Path target = repo.resolve(job.target());

    // ---------- refuse before touching anything ----------
    if (!Files.isRegularFile(target)) {
      return refused("target not found: " + job.target());
    }
    if (options.repairRounds() > 0 && repair == null) {
      return refused("repairRounds > 0 but there is no 'repair' stage in models.json");
    }
    if (options.autoCommit()) {
      try {
        String branch = vcs.currentBranch();
        if (branch.equals("main") || branch.equals("master")) {
          return refused(
              "auto-commit: refusing to commit on '"
                  + branch
                  + "'. switch to a task branch,"
                  + " or use --no-auto-commit");
        }
        if (vcs.isDirty(target)) {
          return refused(
              "auto-commit: "
                  + job.target()
                  + " has uncommitted changes. commit or stash them"
                  + " first, or use --no-auto-commit");
        }
      } catch (VcsException e) {
        return refused("auto-commit: " + e.getMessage());
      }
    }
    String original = repo.read(target);
    try {
      if (MethodLocator.find(original, job.signature()).isEmpty()) {
        int n = MethodLocator.count(original, job.signature());
        return refused(
            n < 0
                ? job.target() + " does not parse, so the method cannot be located"
                : "signature found "
                    + n
                    + " times in "
                    + job.target()
                    + " (must be exactly 1): "
                    + job.signature());
      }
    } catch (IllegalArgumentException e) {
      return refused(e.getMessage());
    }

    RunLog log = RunLog.create(repo, repo.draftsDir().resolve("runs"), name);
    log.write("original.java.bak", original);
    Path promptFile = repo.resolve(job.prompt());
    String firstPrompt = repo.read(promptFile);
    String jobPrompt = firstPrompt;
    String fileName = target.getFileName().toString();

    int total = options.codeAttempts() * (options.repairRounds() + 1);
    say(
        "job=%s model=%s @ %s  (code x%d, repair rounds %d)",
        name, code.model(), code.platform().base(), options.codeAttempts(), options.repairRounds());

    boolean succeeded = false;
    int n = 0;
    Attempt last = null;
    LlmException modelError = null;
    try {
      ladder:
      for (int round = 0; round <= options.repairRounds(); round++) {
        last = null;
        for (int a = 1; a <= options.codeAttempts(); a++) {
          n++;
          String prompt =
              last != null && !last.clarification()
                  ? Prompts.hint(jobPrompt, last.errors())
                  : jobPrompt;
          say("attempt %d/%d (round %d, code %d): calling model...", n, total, round, a);
          last =
              attempt(
                  job,
                  code,
                  target,
                  original,
                  fileName,
                  prompt,
                  "r" + round + "-a" + a,
                  log,
                  options);
          if (last.ok()) {
            succeeded = true;
            break ladder;
          }
          if (last.clarification()) {
            say("coder needs clarification: %s", last.errors());
            break;
          }
          say("compile failed:%n%s", last.errors());
        }
        if (round >= options.repairRounds()) {
          break;
        }
        Optional<String> revised = repair(job, repair, jobPrompt, last, round, log);
        if (revised.isEmpty()) {
          break;
        }
        jobPrompt = revised.get();
      }
    } catch (LlmException e) {
      modelError = e;
    } finally {
      // Runs on success, failure, and exceptions: never leave a broken splice behind.
      if (!succeeded) {
        repo.write(target, original);
      }
    }

    if (modelError != null) {
      return new JobOutcome(
          JobOutcome.Status.MODEL_UNAVAILABLE,
          n,
          log.dir(),
          "model call failed: "
              + modelError.getMessage()
              + ". target restored. see "
              + log.display(),
          null);
    }
    if (!succeeded) {
      if (last != null && last.clarification()) {
        return new JobOutcome(
            JobOutcome.Status.NEEDS_CLARIFICATION,
            n,
            log.dir(),
            "coder needs clarification. target restored. see " + log.display(),
            null);
      }
      return new JobOutcome(
          JobOutcome.Status.FAILED,
          n,
          log.dir(),
          "failed after " + n + " attempt(s). target restored. see " + log.display(),
          null);
    }
    if (!options.compile()) {
      return new JobOutcome(
          JobOutcome.Status.COMPILED,
          n,
          log.dir(),
          "spliced (compile skipped). see " + log.display(),
          null);
    }

    if (!jobPrompt.equals(firstPrompt)) {
      Path orig = promptFile.resolveSibling(promptFile.getFileName() + ".orig");
      if (!Files.exists(orig)) {
        repo.write(orig, firstPrompt);
      }
      repo.write(promptFile, jobPrompt);
      say(
          "repaired prompt saved over %s (original: %s)",
          repo.relative(promptFile), repo.relative(orig));
    }

    String compiled = "compiled after " + n + " attempt(s): " + job.target();
    if (!options.autoCommit()) {
      return new JobOutcome(JobOutcome.Status.COMPILED, n, log.dir(), compiled, null);
    }
    String id = commitId(fileName, job.signature(), name);
    String subject =
        "codegen: "
            + id
            + " ("
            + n
            + " attempt(s)"
            + (n > options.codeAttempts() ? ", repaired" : "")
            + ")";
    String body =
        "Generated by codegen. Plan: "
            + jobFile.toAbsolutePath().getParent().getFileName()
            + ". Model: "
            + code.model()
            + ". Run: "
            + log.display();
    try {
      String sha = vcs.commitOnly(target, subject, body);
      return new JobOutcome(
          JobOutcome.Status.COMPILED, n, log.dir(), compiled + ", committed " + sha, sha);
    } catch (VcsException e) {
      return new JobOutcome(
          JobOutcome.Status.COMMIT_FAILED,
          n,
          log.dir(),
          "compiled, but the commit failed (change left in place, uncommitted): " + e.getMessage(),
          null);
    }
  }

  /** One code-stage call: extract, syntax-check, splice onto the original, compile. */
  private Attempt attempt(
      Job job,
      ResolvedStage code,
      Path target,
      String original,
      String fileName,
      String prompt,
      String tag,
      RunLog log,
      JobOptions options)
      throws LlmException {
    log.write(tag + ".prompt.txt", prompt);
    String raw = chat.complete(code, prompt, null);
    log.write(tag + ".out.txt", raw);

    String cleaned = BodyExtractor.clean(Reasoning.strip(raw));
    if (BodyExtractor.isClarification(cleaned)) {
      return Attempt.question(cleaned);
    }
    String body = BodyExtractor.extractBody(cleaned, job.signature());

    // Cheap check first: a body that does not even parse never reaches the slow Gradle build.
    Optional<String> syntax = BodySyntax.firstError(body);
    if (syntax.isPresent()) {
      log.write(tag + ".syntax.txt", syntax.get());
      return Attempt.failed(body, syntax.get());
    }

    repo.write(target, Splicer.splice(original, job.signature(), body, job.imports()));
    if (!options.compile()) {
      return Attempt.success(body);
    }
    Compiler.CompileResult result = compiler.compile();
    log.write(tag + ".compile.txt", result.output());
    if (result.ok()) {
      return Attempt.success(body);
    }
    return Attempt.failed(body, CompilerErrors.forFile(result.output(), fileName));
  }

  /** The repair stage: returns the revised job prompt, or empty if the reply is unusable. */
  private Optional<String> repair(
      Job job, ResolvedStage repair, String jobPrompt, Attempt last, int round, RunLog log)
      throws LlmException {
    String prompt = Prompts.repair(repairTemplate, jobPrompt, last);
    say("escalating to repair: model=%s @ %s", repair.model(), repair.platform().base());
    log.write("repair-" + round + ".prompt.txt", prompt);
    String raw = chat.complete(repair, prompt, null);
    log.write("repair-" + round + ".out.txt", raw);
    Optional<String> revised = RepairParser.extract(raw, job.signature());
    if (revised.isPresent()) {
      log.write("repair-" + round + ".revised.txt", revised.get());
    } else {
      say("repair reply had no usable REVISED PROMPT block (or it dropped the contract/signature)");
    }
    return revised;
  }

  /**
   * "PlanPrompts.planner" from the target file name and signature, since plan job numbers repeat
   * across plans. Falls back to the job name without its number.
   */
  static String commitId(String fileName, String signature, String jobName) {
    Matcher method = METHOD_NAME.matcher(signature);
    String className =
        fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    return method.find() ? className + "." + method.group(1) : jobName.replaceFirst("^\\d+-", "");
  }

  private static final Pattern METHOD_NAME = Pattern.compile("(\\w+)\\s*\\(");

  /** "01-findByContact.job.json" becomes "01-findByContact". */
  static String jobName(Path jobFile) {
    String file = jobFile.getFileName().toString();
    if (file.endsWith(".json")) {
      file = file.substring(0, file.length() - ".json".length());
    }
    if (file.endsWith(".job")) {
      file = file.substring(0, file.length() - ".job".length());
    }
    return file;
  }

  private JobOutcome refused(String message) {
    return new JobOutcome(JobOutcome.Status.REFUSED, 0, null, message, null);
  }

  private void say(String format, Object... args) {
    out.println(String.format(format, args));
    out.flush();
  }
}
