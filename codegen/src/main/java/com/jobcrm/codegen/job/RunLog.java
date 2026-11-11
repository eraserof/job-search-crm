package com.jobcrm.codegen.job;

import com.jobcrm.codegen.Repo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A timestamped folder that keeps every prompt, reply, and compiler log of one run, e.g. {@code
 * codegen/drafts/runs/01-findByContact-20261106-090233/}.
 */
public final class RunLog {

  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Repo repo;
  private final Path dir;

  private RunLog(Repo repo, Path dir) {
    this.repo = repo;
    this.dir = dir;
  }

  /** Creates {@code parent/<name>-<timestamp>}, adding -2, -3, ... if that already exists. */
  public static RunLog create(Repo repo, Path parent, String name) {
    String base = name + "-" + LocalDateTime.now().format(STAMP);
    try {
      Files.createDirectories(parent);
      for (int n = 1; ; n++) {
        Path dir = parent.resolve(n == 1 ? base : base + "-" + n);
        try {
          Files.createDirectory(dir);
          return new RunLog(repo, dir);
        } catch (FileAlreadyExistsException e) {
          // same second as another run; try the next suffix
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot create run folder under " + parent, e);
    }
  }

  public void write(String fileName, String text) {
    repo.write(dir.resolve(fileName), text == null ? "" : text);
  }

  public Path dir() {
    return dir;
  }

  /** The folder as a repo-relative path, for messages and commit bodies. */
  public String display() {
    return repo.relative(dir);
  }
}
