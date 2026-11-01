package com.jobcrm.agentic.domain;

public interface AgentTool {
  String name();

  String description();

  String jsonSchema(); // JSON Schema for arguments

  ToolResult execute(String argsJson, ToolContext ctx);

  default ToolSpec spec() {
    return new ToolSpec(name(), description(), jsonSchema());
  }
}
