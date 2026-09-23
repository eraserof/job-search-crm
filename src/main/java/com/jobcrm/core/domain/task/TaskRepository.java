package com.jobcrm.core.domain.task;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Task} child entities. */
public interface TaskRepository {

  Optional<Task> findById(TaskId id);

  List<Task> findOpenByOpportunity(OpportunityId opportunity);

  /** Returns open tasks with {@code dueAt <= threshold}. Used by "today" queries. */
  List<Task> findDueBy(Instant threshold);

  void save(Task task);
}
