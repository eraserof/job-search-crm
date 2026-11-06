package com.jobcrm.codegen.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.build.GradleCompiler;
import com.jobcrm.codegen.config.ModelsConfig;
import com.jobcrm.codegen.config.ResolvedStage;
import com.jobcrm.codegen.job.JobRunner;
import com.jobcrm.codegen.llm.ChatClient;
import com.jobcrm.codegen.llm.LlmException;
import com.jobcrm.codegen.llm.ModelLister;
import com.jobcrm.codegen.llm.OpenAiChatClient;
import com.jobcrm.codegen.llm.RetryPolicy;
import com.jobcrm.codegen.llm.RetryingChatClient;
import com.jobcrm.codegen.llm.Sleeper;
import com.jobcrm.codegen.vcs.GitCli;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** The composition root: builds the shared pieces every command uses. */
final class Tool {

  final Repo repo;
  final ObjectMapper json;
  final PrintWriter out;
  final ModelsConfig config;
  final ChatClient chat;
  final ModelLister lister;

  private Tool(Repo repo, ObjectMapper json, PrintWriter out, ModelsConfig config) {
    this.repo = repo;
    this.json = json;
    this.out = out;
    this.config = config;
    this.chat =
        new RetryingChatClient(
            OpenAiChatClient.create(json), RetryPolicy.standard(), Sleeper.system(), out);
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.lister = new ModelLister(http, json);
  }

  static Tool create() {
    ObjectMapper json =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    PrintWriter out =
        new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    Repo repo = Repo.find(Path.of(""));
    ModelsConfig config = ModelsConfig.load(repo.codegenDir().resolve("models.json"), json);
    return new Tool(repo, json, out, config);
  }

  JobRunner jobRunner() {
    return new JobRunner(repo, chat, new GradleCompiler(repo.root()), new GitCli(repo.root()), out);
  }

  Path lockFile() {
    return repo.draftsDir().resolve(".lock");
  }

  /**
   * Fails fast, before any file is touched, if the stage's server is down or lacks the model.
   *
   * @throws LlmException describing what is missing
   */
  void requireModel(ResolvedStage stage) throws LlmException {
    List<String> available = lister.list(stage.platform());
    if (!ModelLister.contains(available, stage.model())) {
      throw LlmException.fatal(
          "model '%s' not on platform '%s'. available: %s"
              .formatted(stage.model(), stage.platformName(), String.join(", ", available)));
    }
  }
}
