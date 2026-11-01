package com.jobcrm.infrastructure.agent;

import com.jobcrm.agentic.domain.AgentDefinition;
import com.jobcrm.agentic.domain.AgentDefinitionRegistry;
import com.jobcrm.agentic.domain.AgentName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SpringAgentDefinitionRegistry implements AgentDefinitionRegistry {
  Map<AgentName, AgentDefinition> byName;

  SpringAgentDefinitionRegistry(List<AgentDefinition> allDefinitions) {
    Map<AgentName, AgentDefinition> map = new HashMap<AgentName, AgentDefinition>();

    for (AgentDefinition d : allDefinitions) {
      map.put(d.name(), d);
    }

    this.byName = Map.copyOf(map);
  }

  @Override
  public AgentDefinition byName(AgentName name) {
    var t = byName.get(name);
    if (t == null) throw new UnknownAgentException("Unknown Agent " + name);
    return t;
  }

  @Override
  public Map<AgentName, AgentDefinition> definitions() {
    return byName;
  }
}
