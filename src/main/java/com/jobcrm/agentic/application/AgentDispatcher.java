package com.jobcrm.agentic.application;

import com.jobcrm.agentic.domain.AgentDefinition;
import com.jobcrm.agentic.domain.AgentDefinitionRegistry;
import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.AgentRunOutcome;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AgentDispatcher {
  private static final Logger log = LoggerFactory.getLogger(AgentDispatcher.class);
  private final Map<AgentName, AgentDefinition> definitions;
  private final Map<AgentName, Semaphore> mutexes;
  private final AgentRunner runner;
  private final ExecutorService vthreads;

  AgentDispatcher(
      AgentDefinitionRegistry definitionsRegistery,
      @Qualifier("agentExecutor") ExecutorService vthreads,
      AgentRunner runner) {
    this.definitions = definitionsRegistery.definitions();

    Map<AgentName, Semaphore> mutexMap = new HashMap<AgentName, Semaphore>();
    // Grab all the possible Agents and add a semphore so that we dont have to deal
    // with null values in the map. Every possible agent has a semaphore
    for (AgentName n : AgentName.values()) mutexMap.put(n, new Semaphore(1));

    this.mutexes = Map.copyOf(mutexMap);
    this.vthreads = vthreads;
    this.runner = runner;
  }

  public AgentRunId dispatch(AgentName name, String inputJson) {
    AgentDefinition def = definitions.get(name);
    if (def == null) {
      throw new AgentRunnerException("No agent registered for " + name);
    }
    Semaphore mutex = mutexes.get(name);
    AgentRunId id = AgentRunId.newId();
    // We just submit because the agent is going to perform some work and the result
    // will be "saved" in the db
    vthreads.submit(
        () -> {
          try {
            mutex.acquire();
            try {
              return runner.run(id, def, inputJson);
            } finally {
              mutex.release();
            }
          } catch (Throwable t) {
            log.error("Agent dispatch {} for {} failed unexpectedly", id, name, t);
            throw t;
          }
        });

    return id;
  }

  public AgentRunOutcome dispatchSync(AgentName name, String inputJson) {
    AgentDefinition def = definitions.get(name);
    if (def == null) {
      throw new AgentRunnerException("No agent registered for " + name);
    }
    Semaphore mutex = mutexes.get(name);
    Future<AgentRunOutcome> future =
        vthreads.submit(
            () -> {
              mutex.acquire();
              try {
                return runner.run(def, inputJson);
              } finally {
                mutex.release();
              }
            });

    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AgentRunnerException("Agent Dispatch interrupted", e);
    } catch (ExecutionException e) {
      throw new AgentRunnerException("Agent run failed", e.getCause());
    }
  }
}
