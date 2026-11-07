package com.jobcrm.codegen.lock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One codegen run per checkout. Two runs at once would compete for the models, splice the same
 * files, and collide on git's index.lock. Use with try-with-resources:
 *
 * <pre>
 * try (RunLock lock = RunLock.acquire(repo.draftsDir().resolve(".lock"), "run plans/x")) { ... }
 * </pre>
 *
 * The lock file holds the owner's pid on line 1 and a description on line 2. A lock whose pid is no
 * longer running is stale (the owner crashed) and is taken over.
 */
public final class RunLock implements AutoCloseable {

  private final Path file;

  private RunLock(Path file) {
    this.file = file;
  }

  /**
   * Creates {@code file} atomically and returns the lock.
   *
   * <ol>
   *   <li>{@code Files.createDirectories(file.getParent())}.
   *   <li>Try {@code Files.writeString(file, content, StandardCharsets.UTF_8,
   *       StandardOpenOption.CREATE_NEW)} where content is {@code ProcessHandle.current().pid() +
   *       "\n" + description + " (started " + Instant.now() + ")\n"}. CREATE_NEW fails if the file
   *       exists, which is what makes this atomic. On success return {@code new RunLock(file)}.
   *   <li>On FileAlreadyExistsException: read the owner with {@link #ownerPid(Path)}. If the pid is
   *       present and {@code ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)} is
   *       true, throw {@code new LockHeldException(file, Files.readString(file).strip())}.
   *       Otherwise the lock is stale: {@code Files.deleteIfExists(file)} and try step 2 once more
   *       (a second FileAlreadyExistsException means another run just took it: throw
   *       LockHeldException).
   *   <li>Any other IOException: throw {@code new UncheckedIOException("cannot create lock " +
   *       file, e)}.
   * </ol>
   */
  public static RunLock acquire(Path file, String description) {
    try {
      Files.createDirectories(file.getParent());
      String content =
          ProcessHandle.current().pid() + "\n" + description + " (started " + Instant.now() + ")\n";
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
          return new RunLock(file);
        } catch (FileAlreadyExistsException e) {
          Optional<Long> owner = ownerPid(file);
          boolean alive =
              owner.isPresent()
                  && ProcessHandle.of(owner.get()).map(ProcessHandle::isAlive).orElse(false);
          if (alive || attempt == 1) {
            throw new LockHeldException(
                file, Files.readString(file, StandardCharsets.UTF_8).strip());
          } else {
            Files.deleteIfExists(file);
          }
        }
      }
      throw new LockHeldException(file, "unknown");
    } catch (IOException e) {
      throw new UncheckedIOException("cannot create lock " + file, e);
    }
  }

  /**
   * The pid on the first line of {@code file}, or empty if the file is missing, empty, or the first
   * line is not a number. Never throws: IOException and NumberFormatException mean empty.
   */
  static Optional<Long> ownerPid(Path file) {
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      if (lines.isEmpty()) return Optional.empty();
      return Optional.of(Long.parseLong(lines.get(0).strip()));
    } catch (IOException | NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** Deletes the lock file; an already-missing file is fine. */
  @Override
  public void close() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot remove lock " + file, e);
    }
  }

  public Path file() {
    return file;
  }
}
