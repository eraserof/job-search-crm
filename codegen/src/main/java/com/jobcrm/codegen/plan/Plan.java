package com.jobcrm.codegen.plan;

import java.util.List;

/** The planner's reply: {@code {"jobs": [...]}}, in the order they must run. */
public record Plan(List<PlanJob> jobs) {
  public Plan {
    jobs = jobs == null ? List.of() : List.copyOf(jobs);
  }
}
