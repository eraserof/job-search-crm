package com.jobcrm.codegen.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * One job file ({@code NN-<id>.job.json}): fill one method body.
 *
 * @param stage models.json stage that writes the code, normally "code"
 * @param target repo-relative path of the Java file
 * @param signature the method signature, as in the target file
 * @param prompt repo-relative path of the prompt file
 * @param imports fully qualified imports to add; never null
 */
public record Job(
    String stage, String target, String signature, String prompt, List<String> imports) {

  public Job {
    imports = imports == null ? List.of() : List.copyOf(imports);
  }

  public static Job load(Path file, ObjectMapper json) {
    try {
      return json.readValue(file.toFile(), Job.class);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read job " + file, e);
    }
  }
}
