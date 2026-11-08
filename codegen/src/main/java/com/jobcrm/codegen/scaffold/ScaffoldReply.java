package com.jobcrm.codegen.scaffold;

import java.util.List;

/**
 * A scaffold reply split into its blocks.
 *
 * @param files complete new source files
 * @param additions members to add to existing classes (reviewed by hand in v1)
 * @param manual changes the model was not allowed to make, one per entry
 * @param problems structural problems found while parsing, e.g. a block that never closed
 */
public record ScaffoldReply(
    List<NewFile> files, List<AddTo> additions, List<String> manual, List<String> problems) {

  public ScaffoldReply {
    files = List.copyOf(files);
    additions = List.copyOf(additions);
    manual = List.copyOf(manual);
    problems = List.copyOf(problems);
  }

  /** A complete new file at a repo-relative path. */
  public record NewFile(String path, String source) {}

  /** Imports and members to add inside {@code className} in the existing file at {@code path}. */
  public record AddTo(String path, String className, String body) {}
}
