package com.jobcrm.codegen.scaffold;

import com.jobcrm.codegen.Repo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies the reviewed {@code files/} tree of a scaffold draft into the repo. All or nothing: if any
 * target already exists, nothing is written.
 */
public final class ScaffoldApplier {

  private final Repo repo;

  public ScaffoldApplier(Repo repo) {
    this.repo = repo;
  }

  /**
   * @param draftDir a folder produced by {@link Scaffolder}
   * @return the repo-relative paths written
   * @throws IllegalArgumentException if the folder has no files/ tree, is empty, or a target exists
   */
  public List<String> apply(Path draftDir) {
    Path filesDir = draftDir.resolve("files");
    if (!Files.isDirectory(filesDir)) {
      throw new IllegalArgumentException("no files/ folder in " + repo.relative(draftDir));
    }
    List<Path> sources = listFiles(filesDir);
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("files/ is empty in " + repo.relative(draftDir));
    }
    List<String> taken = new ArrayList<>();
    for (Path source : sources) {
      String rel = filesDir.relativize(source).toString().replace('\\', '/');
      if (Files.exists(repo.resolve(rel))) {
        taken.add(rel);
      }
    }
    if (!taken.isEmpty()) {
      throw new IllegalArgumentException(
          "already exist, nothing written: " + String.join(", ", taken));
    }
    List<String> written = new ArrayList<>();
    for (Path source : sources) {
      String rel = filesDir.relativize(source).toString().replace('\\', '/');
      repo.write(repo.resolve(rel), repo.read(source));
      written.add(rel);
    }
    return written;
  }

  private static List<Path> listFiles(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + dir, e);
    }
  }
}
