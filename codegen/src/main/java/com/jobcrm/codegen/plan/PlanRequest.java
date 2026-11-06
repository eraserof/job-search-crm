package com.jobcrm.codegen.plan;

import java.util.List;

/**
 * A request file in {@code codegen/requests/}: what to build and which files the planner sees.
 *
 * @param ask the feature or fix, in plain words
 * @param context repo-relative files inlined whole into the planner prompt; never null
 */
public record PlanRequest(String ask, List<String> context) {
  public PlanRequest {
    context = context == null ? List.of() : List.copyOf(context);
  }
}
