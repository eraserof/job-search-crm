package com.jobcrm.agentic.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AgentRun {
  private final AgentRunId id;

  private final AgentName agent;

  private final Instant startedAt;

  private Instant finishedAt;

  private AgentRunStatus status; // RUNNING, SUCCESS, FAILED, ABORTED

  private final String inputJson;

  private final List<AgentTurn> turns; // append-only

  private String finalOutput;

  private TokenUsage tokens;

  private String errorMessage;

  private AgentRun(AgentName agent, String inputJson, Instant startAt) {
    this.id = AgentRunId.newId();
    this.turns = new ArrayList<AgentTurn>();
    this.agent = agent;
    this.startedAt = startAt;
    this.inputJson = inputJson;
    this.tokens = TokenUsage.ZERO;
    this.status = AgentRunStatus.RUNNING;
  }

  /**
   * id TEXT PRIMARY KEY, agent TEXT NOT NULL, status TEXT NOT NULL, started_at TEXT NOT NULL,
   * finished_at TEXT, input_json TEXT NOT NULL, final_output TEXT, input_tokens INTEGER NOT NULL
   * DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0, error_message TEXT
   */
  private AgentRun(
      AgentRunId id,
      AgentName agent,
      AgentRunStatus status,
      Instant startAt,
      Instant finishedAt,
      String inputJson,
      String finalOutput,
      TokenUsage tokens,
      List<AgentTurn> turns,
      String errorMessage) {
    this.id = Objects.requireNonNull(id, "id");
    this.agent = Objects.requireNonNull(agent, "agent");
    this.status = Objects.requireNonNull(status, "status");
    this.startedAt = Objects.requireNonNull(startAt, "startAt");
    this.finishedAt = finishedAt;
    this.inputJson = Objects.requireNonNull(inputJson, "inputJson");
    this.finalOutput = finalOutput;
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.turns = new ArrayList<AgentTurn>();
    this.turns.addAll(turns);
    this.errorMessage = errorMessage;
  }

  public static AgentRun reconstitute(
      AgentRunId id,
      AgentName agent,
      AgentRunStatus status,
      Instant startAt,
      Instant finishedAt,
      String inputJson,
      String finalOutput,
      TokenUsage tokens,
      List<AgentTurn> turns,
      String errorMessage) {
    AgentRun agentRun =
        new AgentRun(
            id,
            agent,
            status,
            startAt,
            finishedAt,
            inputJson,
            finalOutput,
            tokens,
            turns,
            errorMessage);

    return agentRun;
  }

  public static AgentRun start(AgentName name, String inputJson, Instant now) {
    return new AgentRun(name, inputJson, now);
  }

  public List<AgentTurn> turns() {
    return List.copyOf(this.turns);
  }

  public AgentRunStatus status() {
    return status;
  }

  public AgentRunId id() {
    return id;
  }

  public AgentName agent() {
    return agent;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public String inputJson() {
    return inputJson;
  }

  public TokenUsage tokens() {
    return tokens;
  }

  public Optional<String> errorMessage() {
    return Optional.ofNullable(errorMessage);
  }

  public Optional<String> finalOutput() {
    return Optional.ofNullable(finalOutput);
  }

  public Optional<Instant> finishedAt() {
    return Optional.ofNullable(finishedAt);
  }

  public void appendTurn(AgentTurn t) {
    if (this.status != AgentRunStatus.RUNNING) throw new IllegalAgentRunState(this.status);
    this.turns.add(t);
  }

  public void finish(String output, TokenUsage totals, Instant now) {
    if (this.status != AgentRunStatus.RUNNING)
      throw new IllegalAgentRunState(this.status, AgentRunStatus.SUCCESS);

    this.finishedAt = now;
    this.finalOutput = output;
    this.tokens = this.tokens.add(totals);
    this.status = AgentRunStatus.SUCCESS;
  }

  public void fail(String error, Instant now) {
    if (this.status != AgentRunStatus.RUNNING)
      throw new IllegalAgentRunState(this.status, AgentRunStatus.FAILED);
    this.errorMessage = error;
    this.status = AgentRunStatus.FAILED;
    this.finishedAt = now;
  }

  public void abort(String reason, Instant now) {
    if (this.status != AgentRunStatus.RUNNING)
      throw new IllegalAgentRunState(this.status, AgentRunStatus.ABORTED);
    this.errorMessage = reason;
    this.status = AgentRunStatus.ABORTED;
    this.finishedAt = now;
  }
}
