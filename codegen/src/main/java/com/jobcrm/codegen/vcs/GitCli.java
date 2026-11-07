package com.jobcrm.codegen.vcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** {@link Vcs} backed by the {@code git} command line, run in the repo root. */
public final class GitCli implements Vcs {

  private final Path root;

  public GitCli(Path root) {
    this.root = root;
  }

  /**
   * Runs {@code git} with {@code args} in {@link #root} and returns its trimmed output.
   *
   * <ol>
   *   <li>Build the command: a new ArrayList containing "git", then all of {@code args}.
   *   <li>{@code new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
   *       .start()}.
   *   <li>Read the output with {@code new String(process.getInputStream().readAllBytes(),
   *       StandardCharsets.UTF_8)} BEFORE {@code process.waitFor()}.
   *   <li>Non-zero exit code: build {@code String cmd = String.join(" ", args);} and throw {@code
   *       new VcsException("git " + cmd + " failed: " + output.strip())}.
   *   <li>Return {@code output.strip()}.
   *   <li>IOException: throw {@code new VcsException("cannot run git", e)}. InterruptedException:
   *       re-set the interrupt flag and throw {@code new VcsException("interrupted", e)}.
   * </ol>
   */
  String git(List<String> args) throws VcsException {
    ArrayList<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(args);

    ProcessBuilder processBuilder = new ProcessBuilder(command)
            .directory(root.toFile())
            .redirectErrorStream(true);

    try {
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();

        if (exit != 0) {
            String cmd = String.join(" ", args);
            throw new VcsException("git " + cmd + " failed: " + output.strip());
        }

        return output.strip();
    } catch (IOException e) {
        throw new VcsException("cannot run git", e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VcsException("interrupted", e);
    }
  }

  /** {@code git rev-parse --abbrev-ref HEAD}. */
  @Override
  public String currentBranch() throws VcsException {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * {@code git status --porcelain -- <path>} where path is {@code root.relativize(file)} with
   * forward slashes; dirty when the output is not blank.
   */
  @Override
  public boolean isDirty(Path file) throws VcsException {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * With path = {@code root.relativize(file)} using forward slashes: run {@code git add --
   * <path>}, then {@code git commit -q -m <subject> -m <body> -- <path>} (a pathspec makes git
   * commit only that file), then return {@code git rev-parse --short HEAD}.
   */
  @Override
  public String commitOnly(Path file, String subject, String body) throws VcsException {
    throw new UnsupportedOperationException("TODO(codegen)");
  }
}
