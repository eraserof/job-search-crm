package com.jobcrm.codegen.plan;

import java.util.List;

/**
 * One planned method body, exactly as the planner returns it. Lists are never null.
 *
 * @param id letters, digits, _ or -; used in file names
 * @param target repo-relative path of the Java file
 * @param signature copied verbatim from the target, without annotations
 * @param classContext class declaration plus the fields and helpers the body may use
 * @param types exact signatures of every other type and method the body may call
 * @param behavior ordered, concrete steps
 * @param imports fully qualified imports the target does not already have
 */
public record PlanJob(
    String id,
    String target,
    String signature,
    String classContext,
    String types,
    List<String> behavior,
    List<String> imports) {

  public PlanJob {
    behavior = behavior == null ? List.of() : List.copyOf(behavior);
    imports = imports == null ? List.of() : List.copyOf(imports);
  }
}
