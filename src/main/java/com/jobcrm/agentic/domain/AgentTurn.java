package com.jobcrm.agentic.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * id TEXT PRIMARY KEY, agent_run_id TEXT NOT NULL REFERENCES agent_run (id) ON DELETE CASCADE,
 * turn_index INTEGER NOT NULL, role TEXT NOT NULL, -- SYSTEM, USER, ASSISTANT, TOOL content_json
 * TEXT NOT NULL, -- System/User text, Assistant contentJson, or tool-call payload for TOOL turns
 * tool_name TEXT, tool_args_json TEXT, tool_result_json TEXT, created_at TEXT NOT NULL, UNIQUE
 * (agent_run_id, turn_index)
 */
public sealed interface AgentTurn {
  int index();

  Instant createdAt();

  record Assistant(int index, Instant createdAt, String contentJson, List<ToolCall> toolCalls)
      implements AgentTurn {
    public Assistant {
      Objects.requireNonNull(contentJson);
      Objects.requireNonNull(toolCalls);
      toolCalls = List.copyOf(toolCalls);
    }
  }

  record ToolResultTurn(int index, Instant createdAt, ToolCall call, ToolResult result)
      implements AgentTurn {
    public ToolResultTurn {
      Objects.requireNonNull(result);
      Objects.requireNonNull(call);
    }
  }

  record System(int index, Instant createdAt, String content) implements AgentTurn {}

  record User(int index, Instant createdAt, String content) implements AgentTurn {}
}
