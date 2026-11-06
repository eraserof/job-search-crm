package com.jobcrm.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The repository the tool works in: path resolution and UTF-8 file IO relative to the root. */
public final class Repo {

  private final Path root;

  public Repo(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  /**
   * Walks up from {@code start} to the first directory containing both {@code settings.gradle.kts}
   * and a {@code codegen} folder. Throws IllegalStateException if there is none.
   */
  public static Repo find(Path start) {
    Path dir = start.toAbsolutePath().normalize();
    while (dir != null) {
      if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))
          && Files.isDirectory(dir.resolve("codegen"))) {
        return new Repo(dir);
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("not inside the repo (no settings.gradle.kts + codegen/)");
  }

  public Path root() {
    return root;
  }

  /** Resolves a repo-relative path (either slash style) or returns an absolute one unchanged. */
  public Path resolve(String path) {
    Path p = Path.of(path);
    return p.isAbsolute() ? p.normalize() : root.resolve(p).normalize();
  }

  /** Repo-relative path with forward slashes, as stored in job and plan files. */
  public String relative(Path path) {
    return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }

  public String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }

  /** Writes UTF-8 without a BOM, creating parent directories. */
  public void write(Path file, String text) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write " + file, e);
    }
  }

  public Path codegenDir() {
    return root.resolve("codegen");
  }

  public Path draftsDir() {
    return codegenDir().resolve("drafts");
  }
}
