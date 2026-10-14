package com.jobcrm.infrastructure.persistence;

import static com.jobcrm.infrastructure.persistence.SqlTypes.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRun;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.AgentRunRepository;
import com.jobcrm.agentic.domain.AgentRunStatus;
import com.jobcrm.agentic.domain.AgentTurn;
import com.jobcrm.agentic.domain.AssistantPayload;
import com.jobcrm.agentic.domain.RunRow;
import com.jobcrm.agentic.domain.TokenUsage;
import com.jobcrm.agentic.domain.ToolCall;
import com.jobcrm.agentic.domain.ToolResult;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JdbcAgentRunRepository implements AgentRunRepository {

  private final JdbcSupport jdbc;
  private final ObjectMapper json;

  JdbcAgentRunRepository(JdbcSupport jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Optional<AgentRun> findById(AgentRunId id) {
    Optional<RunRow> row =
        jdbc.queryOne(
            selectColumns() + " WHERE id = ?",
            ps -> ps.setString(1, uuid(id.value())),
            this::mapRunRow);
    if (row.isEmpty()) {
      return Optional.empty();
    }
    RunRow r = row.get();
    List<AgentTurn> turns = findAgentTurnsById(r.id());

    return Optional.of(
        AgentRun.reconstitute(
            r.id(),
            r.agent(),
            r.status(),
            r.startedAt(),
            r.finishedAt(),
            r.inputJson(),
            r.finalOutput(),
            r.tokens(),
            turns,
            r.errorMessage()));
  }

  /**
   * id TEXT PRIMARY KEY, agent TEXT NOT NULL, status TEXT NOT NULL, -- RUNNING, SUCCESS, FAILED,
   * ABORTED started_at TEXT NOT NULL, finished_at TEXT, input_json TEXT NOT NULL, final_output
   * TEXT, input_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0,
   * error_message TEXT
   */
  @Override
  public void save(AgentRun agentRun) {
    jdbc.inWriteTransaction(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO agent_run (id, agent, status, started_at, finished_at, "
                      + "input_json, final_output, input_tokens, output_tokens, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET status = excluded.status, final_output = excluded.final_output, finished_at = excluded.finished_at, input_tokens = excluded.input_tokens, output_tokens = excluded.output_tokens, error_message = excluded.error_message ")) {

            Instant finishedAt = agentRun.finishedAt().orElse(null);

            ps.setString(1, uuid(agentRun.id().value()));
            ps.setString(2, agentRun.agent().name());
            ps.setString(3, agentRun.status().name());
            ps.setString(4, instant(agentRun.startedAt()));
            setNullableString(ps, 5, finishedAt == null ? null : instant(finishedAt));
            ps.setString(6, agentRun.inputJson());
            setNullableString(ps, 7, agentRun.finalOutput().orElse(null));
            ps.setLong(8, agentRun.tokens().inputTokens());
            ps.setLong(9, agentRun.tokens().outputTokens());
            setNullableString(ps, 10, agentRun.errorMessage().orElse(null));
            ps.executeUpdate();
          }

          /**
           * id TEXT PRIMARY KEY, agent_run_id TEXT NOT NULL REFERENCES agent_run (id) ON DELETE
           * CASCADE, turn_index INTEGER NOT NULL, role TEXT NOT NULL, -- SYSTEM, USER, ASSISTANT,
           * TOOL 5* content_json TEXT NOT NULL, -- System/User text, Assistant contentJson, or 6
           * tool_name TEXT, 7 tool_args_json TEXT, 8 tool_result_json TEXT, 9 created_at TEXT NOT
           * NULL, UNIQUE (agent_run_id, turn_index)
           */
          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO agent_turn (id, agent_run_id, turn_index, role, content_json, tool_name, tool_args_json, tool_result_json, created_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(agent_run_id, turn_index) DO NOTHING")) {
            for (AgentTurn ref : agentRun.turns()) {
              ins.setString(1, uuid(UUID.randomUUID()));
              ins.setString(2, uuid(agentRun.id().value()));
              ins.setInt(3, ref.index());
              ins.setString(9, instant(ref.createdAt()));

              switch (ref) {
                case AgentTurn.Assistant a -> {
                  ins.setString(4, "ASSISTANT");
                  ins.setString(5, writeJson(new AssistantPayload(a.contentJson(), a.toolCalls())));
                  ins.setString(6, null);
                  ins.setString(7, null);
                  ins.setString(8, null);
                }
                case AgentTurn.System s -> {
                  ins.setString(4, "SYSTEM");
                  ins.setString(5, s.content());
                  ins.setString(6, null);
                  ins.setString(7, null);
                  ins.setString(8, null);
                }
                case AgentTurn.ToolResultTurn t -> {
                  ins.setString(4, "TOOL");
                  ins.setString(5, "{}");
                  ins.setString(6, t.call().name());
                  ins.setString(7, writeJson(t.call()));
                  ins.setString(8, writeJson(t.result()));
                }
                case AgentTurn.User u -> {
                  ins.setString(4, "USER");
                  ins.setString(5, u.content());
                  ins.setString(6, null);
                  ins.setString(7, null);
                  ins.setString(8, null);
                }
              }
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  /**
   * id TEXT PRIMARY KEY, agent_run_id TEXT NOT NULL REFERENCES agent_run (id) ON DELETE CASCADE,
   * turn_index INTEGER NOT NULL, role TEXT NOT NULL, -- SYSTEM, USER, ASSISTANT, TOOL content_json
   * TEXT NOT NULL, -- System/User text, Assistant contentJson, or tool-call payload for TOOL turns
   * tool_name TEXT, tool_args_json TEXT, tool_result_json TEXT, created_at TEXT NOT NULL,
   */
  private List<AgentTurn> findAgentTurnsById(AgentRunId id) {
    return jdbc.queryList(
        "SELECT id, agent_run_id, turn_index, role, content_json, tool_name, tool_args_json, tool_result_json, created_at FROM agent_turn WHERE agent_run_id = ? ORDER BY turn_index",
        ps -> ps.setString(1, uuid(id.value())),
        this::mapRowAgentTurn);
  }

  private <T> T readJson(String v, Class<T> type) {
    try {
      return json.readValue(v, type);
    } catch (JsonProcessingException e) {
      throw new PersistenceException("JSON read failed", e);
    }
  }

  private String writeJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new PersistenceException("JSON write failed", e);
    }
  }

  /**
   * id TEXT PRIMARY KEY, agent TEXT NOT NULL, status TEXT NOT NULL, -- RUNNING, SUCCESS, FAILED,
   * ABORTED started_at TEXT NOT NULL, finished_at TEXT, input_json TEXT NOT NULL, final_output
   * TEXT, input_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0,
   * error_message TEXT
   */
  private static String selectColumns() {
    return "SELECT id, agent, status, started_at, finished_at, input_json, final_output, input_tokens, output_tokens, error_message "
        + "FROM agent_run";
  }

  private RunRow mapRunRow(ResultSet rs) throws SQLException {
    AgentRunId id = new AgentRunId(uuid(rs.getString("id")));
    AgentName agent = AgentName.valueOf(rs.getString("agent"));
    AgentRunStatus status = AgentRunStatus.valueOf(rs.getString("status"));
    Instant startAt = instant(rs.getString("started_at"));
    Instant finishedAt = nullableInstant(rs, "finished_at");
    String inputJson = rs.getString("input_json");
    String finalOutput = rs.getString("final_output");
    TokenUsage tokens = new TokenUsage(rs.getLong("input_tokens"), rs.getLong("output_tokens"));
    String errorMessage = rs.getString("error_message");
    return new RunRow(
        id, agent, status, startAt, finishedAt, inputJson, finalOutput, tokens, errorMessage);
  }

  /**
   * id TEXT PRIMARY KEY, agent_run_id TEXT NOT NULL REFERENCES agent_run (id) ON DELETE CASCADE,
   * turn_index INTEGER NOT NULL, role TEXT NOT NULL, -- SYSTEM, USER, ASSISTANT, TOOL content_json
   * TEXT NOT NULL, -- System/User text, Assistant contentJson, or tool_name TEXT, tool_args_json
   * TEXT, tool_result_json TEXT, created_at TEXT NOT NULL,
   */
  private AgentTurn mapRowAgentTurn(ResultSet rs) throws SQLException {
    AgentTurn turn;
    String role = rs.getString("role");
    switch (role) {
      case "SYSTEM":
        turn =
            new AgentTurn.System(
                rs.getInt("turn_index"),
                instant(rs.getString("created_at")),
                rs.getString("content_json"));
        break;
      case "USER":
        turn =
            new AgentTurn.User(
                rs.getInt("turn_index"),
                instant(rs.getString("created_at")),
                rs.getString("content_json"));
        break;
      case "ASSISTANT":
        // Need to grab the List<ToolCall> toolCalls
        AssistantPayload p = readJson(rs.getString("content_json"), AssistantPayload.class);
        turn =
            new AgentTurn.Assistant(
                rs.getInt("turn_index"),
                instant(rs.getString("created_at")),
                p.contentJson(),
                p.toolCalls());
        break;
      case "TOOL":
        ToolCall toolCall = readJson(rs.getString("tool_args_json"), ToolCall.class);

        ToolResult toolResult = readJson(rs.getString("tool_result_json"), ToolResult.class);

        turn =
            new AgentTurn.ToolResultTurn(
                rs.getInt("turn_index"), instant(rs.getString("created_at")), toolCall, toolResult);
        break;
      default:
        throw new PersistenceException("Agent " + role + " not supported", null);
    }
    return turn;
  }
}
