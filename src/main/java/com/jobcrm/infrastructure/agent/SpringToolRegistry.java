package com.jobcrm.infrastructure.agent;

import com.jobcrm.agentic.domain.AgentTool;
import com.jobcrm.agentic.domain.ToolRegistry;
import com.jobcrm.agentic.domain.ToolSpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SpringToolRegistry implements ToolRegistry {

  Map<String, AgentTool> byName;

  SpringToolRegistry(List<AgentTool> allTools) {
    Map<String, AgentTool> map = new HashMap<>();
    for (AgentTool tool : allTools) {
      map.put(tool.name(), tool);
    }
    this.byName = Map.copyOf(map);
  }

  @Override
  public AgentTool byName(String name) {
    var t = byName.get(name);
    if (t == null) throw new UnknownToolException("Unknown Tool " + name);
    return t;
  }

  @Override
  public List<ToolSpec> specs(Collection<String> names) {
    return names.stream().map(n -> byName(n).spec()).toList();
  }
}
