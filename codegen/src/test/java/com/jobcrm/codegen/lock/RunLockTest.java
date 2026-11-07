package com.jobcrm.codegen.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunLockTest {

  @TempDir Path dir;

  @Test
  void acquireWritesThePidAndDescriptionAndCloseRemovesTheFile() throws Exception {
    Path file = dir.resolve("drafts/.lock");

    try (RunLock lock = RunLock.acquire(file, "run plans/x")) {
      assertThat(lock.file()).isEqualTo(file);
      assertThat(Files.readAllLines(file).get(0))
          .isEqualTo(String.valueOf(ProcessHandle.current().pid()));
      assertThat(Files.readString(file)).contains("run plans/x").contains("started");
    }

    assertThat(file).doesNotExist();
  }

  @Test
  void aSecondRunWhileTheFirstIsAliveIsRefused() {
    Path file = dir.resolve(".lock");

    try (RunLock first = RunLock.acquire(file, "first")) {
      assertThatThrownBy(() -> RunLock.acquire(file, "second"))
          .isInstanceOf(LockHeldException.class)
          .hasMessageContaining("another codegen run is active")
          .hasMessageContaining("first");
      assertThat(file).exists();
    }
  }

  @Test
  void aStaleLockFromADeadProcessIsTakenOver() throws Exception {
    Path file = dir.resolve(".lock");
    Files.writeString(file, "999999999\nold run (started long ago)\n");

    try (RunLock lock = RunLock.acquire(file, "new run")) {
      assertThat(Files.readString(file)).contains("new run").doesNotContain("old run");
    }
  }

  @Test
  void aLockWithAnUnreadableOwnerIsTreatedAsStale() throws Exception {
    Path file = dir.resolve(".lock");
    Files.writeString(file, "not a pid\n");

    try (RunLock lock = RunLock.acquire(file, "new run")) {
      assertThat(Files.readString(file)).contains("new run");
    }
  }

  @Test
  void ownerPidReadsTheFirstLineOnly() throws Exception {
    Path file = dir.resolve(".lock");
    Files.writeString(file, "  1234  \nrun x\n");

    assertThat(RunLock.ownerPid(file)).contains(1234L);
  }

  @Test
  void ownerPidIsEmptyForMissingEmptyOrGarbageFiles() throws Exception {
    Path empty = dir.resolve("empty");
    Files.writeString(empty, "");
    Path garbage = dir.resolve("garbage");
    Files.writeString(garbage, "abc\n");

    assertThat(RunLock.ownerPid(dir.resolve("missing"))).isEmpty();
    assertThat(RunLock.ownerPid(empty)).isEmpty();
    assertThat(RunLock.ownerPid(garbage)).isEmpty();
  }
}
