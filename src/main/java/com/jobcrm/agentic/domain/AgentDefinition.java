package com.jobcrm.agentic.domain;

import java.util.Set;

public interface AgentDefinition {
  AgentName name();

  String systemPrompt();

  Set<String> allowedToolNames();

  int maxSteps();

  LlmClient llmClient(); // pre-bound with per-agent params

  LlmModelParams params();
}
