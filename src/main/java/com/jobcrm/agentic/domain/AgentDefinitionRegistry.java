package com.jobcrm.agentic.domain;

import java.util.Map;

public interface AgentDefinitionRegistry {
  AgentDefinition byName(AgentName name);

  Map<AgentName, AgentDefinition> definitions();
}
