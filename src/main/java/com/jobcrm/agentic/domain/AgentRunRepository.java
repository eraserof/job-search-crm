package com.jobcrm.agentic.domain;

import java.util.Optional;

public interface AgentRunRepository {
  Optional<AgentRun> findById(AgentRunId id);

  void save(AgentRun agentRun);
}
