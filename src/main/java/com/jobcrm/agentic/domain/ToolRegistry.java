package com.jobcrm.agentic.domain;

import java.util.Collection;
import java.util.List;

public interface ToolRegistry {
  AgentTool byName(String name); // throws if unknown

  List<ToolSpec> specs(Collection<String> names);
}
