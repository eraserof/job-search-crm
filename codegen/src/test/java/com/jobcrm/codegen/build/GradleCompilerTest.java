package com.jobcrm.codegen.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleCompilerTest {

  @TempDir Path root;

  private static final boolean WINDOWS =
      System.getProperty("os.name").toLowerCase().startsWith("windows");

  @Test
  void usesTheFullWrapperPathOnWindows() {
    String saved = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Windows 11");

      assertThat(new GradleCompiler(root).command())
          .isEqualTo(
              List.of(
                  "cmd.exe",
                  "/c",
                  root.resolve("gradlew.bat").toString(),
                  "classes",
                  "testClasses",
                  "--console=plain",
                  "-q"));
    } finally {
      System.setProperty("os.name", saved);
    }
  }

  @Test
  void usesTheFullWrapperPathElsewhere() {
    String saved = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Linux");

      assertThat(new GradleCompiler(root).command())
          .isEqualTo(
              List.of(
                  root.resolve("gradlew").toString(),
                  "classes",
                  "testClasses",
                  "--console=plain",
                  "-q"));
    } finally {
      System.setProperty("os.name", saved);
    }
  }

  @Test
  void reportsSuccessAndCapturesOutput() throws Exception {
    wrapper(0, "compiled fine");

    Compiler.CompileResult result = new GradleCompiler(root).compile();

    assertThat(result.ok()).isTrue();
    assertThat(result.output()).contains("compiled fine").contains("classes");
  }

  @Test
  void reportsFailureWithTheOutput() throws Exception {
    wrapper(3, "A.java:3: error: boom");

    Compiler.CompileResult result = new GradleCompiler(root).compile();

    assertThat(result.ok()).isFalse();
    assertThat(result.output()).contains("A.java:3: error: boom");
  }

  /** A stand-in for gradlew that prints a line plus its first argument and exits with a code. */
  private void wrapper(int exitCode, String line) throws Exception {
    if (WINDOWS) {
      Files.writeString(
          root.resolve("gradlew.bat"),
          "@echo off\r\necho " + line + "\r\necho %1\r\nexit /b " + exitCode + "\r\n");
    } else {
      Path script = root.resolve("gradlew");
      Files.writeString(
          script, "#!/bin/sh\necho \"" + line + "\"\necho \"$1\"\nexit " + exitCode + "\n");
      script.toFile().setExecutable(true);
    }
  }
}
