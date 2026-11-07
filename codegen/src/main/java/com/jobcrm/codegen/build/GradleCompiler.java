package com.jobcrm.codegen.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs {@code gradlew classes testClasses} in the repo root. Run from the root, those tasks cover
 * every Gradle project, so test sources must compile too.
 */
public final class GradleCompiler implements Compiler {

  private final Path root;

  public GradleCompiler(Path root) {
    this.root = root;
  }

  /**
   * The command to run. On Windows ({@code System.getProperty("os.name")} starts with "Windows",
   * case-insensitive): {@code ["cmd.exe", "/c", root.resolve("gradlew.bat").toString(), "classes",
   * "testClasses", "--console=plain", "-q"]}. Otherwise: {@code
   * [root.resolve("gradlew").toString(), "classes", "testClasses", "--console=plain", "-q"]}. The
   * full path matters: this machine sets NoDefaultCurrentDirectoryInExePath, so a bare
   * "gradlew.bat" is not found.
   */
  List<String> command() {
    boolean windows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    if (windows) {
      return List.of(
          "cmd.exe",
          "/c",
          root.resolve("gradlew.bat").toString(),
          "classes",
          "testClasses",
          "--console=plain",
          "-q");
    } else {
      return List.of(
          root.resolve("gradlew").toString(), "classes", "testClasses", "--console=plain", "-q");
    }
  }

  /**
   * Runs {@link #command()} and waits for it.
   *
   * <ol>
   *   <li>{@code new ProcessBuilder(command()).directory(root.toFile()).redirectErrorStream(true)}
   *       then {@code start()}.
   *   <li>Read all output first with {@code new String(process.getInputStream().readAllBytes(),
   *       StandardCharsets.UTF_8)}, then {@code process.waitFor()}. Reading before waiting avoids a
   *       deadlock when the output fills the pipe buffer.
   *   <li>Return {@code new CompileResult(exitCode == 0, output)}.
   *   <li>IOException: throw {@code new UncheckedIOException("cannot run gradle", e)}.
   *       InterruptedException: re-set the interrupt flag and return {@code new
   *       CompileResult(false, "interrupted")}.
   * </ol>
   */
  @Override
  public CompileResult compile() {
    try {
      Process process =
          new ProcessBuilder(command()).directory(root.toFile()).redirectErrorStream(true).start();

      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      return new CompileResult(exitCode == 0, output);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot run gradle", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CompileResult(false, "interrupted");
    }
  }
}
