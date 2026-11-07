package com.jobcrm.codegen.vcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs real git against a throwaway repository; only that repository's own config is set. */
class GitCliTest {

  @TempDir Path root;
  private GitCli git;

  @BeforeEach
  void repo() throws Exception {
    git = new GitCli(root);
    git.git(List.of("init", "-q"));
    git.git(List.of("config", "user.name", "Codegen Test"));
    git.git(List.of("config", "user.email", "codegen@test.invalid"));
    git.git(List.of("config", "commit.gpgsign", "false"));
    Files.writeString(root.resolve("a.txt"), "a\n");
    Files.writeString(root.resolve("b.txt"), "b\n");
    git.git(List.of("add", "--", "a.txt", "b.txt"));
    git.git(List.of("commit", "-q", "-m", "initial"));
    git.git(List.of("checkout", "-q", "-b", "feature/x"));
  }

  @Test
  void currentBranchIsTheCheckedOutBranch() throws Exception {
    assertThat(git.currentBranch()).isEqualTo("feature/x");
  }

  @Test
  void isDirtyIsFalseForACleanFileAndTrueForEditedOrUntrackedOnes() throws Exception {
    assertThat(git.isDirty(root.resolve("a.txt"))).isFalse();

    Files.writeString(root.resolve("a.txt"), "changed\n");
    assertThat(git.isDirty(root.resolve("a.txt"))).isTrue();
    assertThat(git.isDirty(root.resolve("b.txt"))).isFalse();

    Files.writeString(root.resolve("new.txt"), "n\n");
    assertThat(git.isDirty(root.resolve("new.txt"))).isTrue();
  }

  @Test
  void commitOnlyCommitsTheNamedFileAndLeavesOtherChangesAlone() throws Exception {
    Files.writeString(root.resolve("a.txt"), "changed a\n");
    Files.writeString(root.resolve("b.txt"), "changed b\n");
    git.git(List.of("add", "--", "b.txt")); // staged, but must not be swept into a's commit

    String sha = git.commitOnly(root.resolve("a.txt"), "codegen: A.f (1 attempt(s))", "details");

    assertThat(sha).isEqualTo(git.git(List.of("rev-parse", "--short", "HEAD")));
    assertThat(git.git(List.of("show", "--name-only", "--format=%s", "HEAD")))
        .isEqualTo("codegen: A.f (1 attempt(s))\n\na.txt");
    assertThat(git.git(List.of("log", "-1", "--format=%b"))).isEqualTo("details");
    assertThat(git.isDirty(root.resolve("a.txt"))).isFalse();
    assertThat(git.isDirty(root.resolve("b.txt"))).isTrue();
  }

  @Test
  void aFailingCommandSaysWhichOneAndWhy() {
    assertThatThrownBy(() -> git.git(List.of("not-a-git-command")))
        .isInstanceOf(VcsException.class)
        .hasMessageStartingWith("git not-a-git-command failed: ");
  }
}
