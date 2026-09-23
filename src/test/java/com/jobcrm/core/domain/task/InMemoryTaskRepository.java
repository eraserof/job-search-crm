package com.jobcrm.core.domain.task;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link TaskRepository}. */
public final class InMemoryTaskRepository implements TaskRepository {

  private final Map<TaskId, Task> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Task> findById(TaskId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<Task> findOpenByOpportunity(OpportunityId opportunity) {
    Objects.requireNonNull(opportunity, "opportunity");
    return byId.values().stream()
        .filter(t -> t.opportunity().equals(opportunity))
        .filter(t -> t.status() == TaskStatus.OPEN)
        .toList();
  }

  @Override
  public List<Task> findDueBy(Instant threshold) {
    Objects.requireNonNull(threshold, "threshold");
    return byId.values().stream()
        .filter(t -> t.status() == TaskStatus.OPEN)
        .filter(t -> !t.dueAt().isAfter(threshold))
        .toList();
  }

  @Override
  public void save(Task task) {
    Objects.requireNonNull(task, "task");
    byId.put(task.id(), task);
  }

  public List<Task> all() {
    return List.copyOf(byId.values());
  }
}
