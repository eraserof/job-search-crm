package com.jobcrm.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.rules.RulesEngine;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Task 11 scheduling primitives are wired into the Spring context: a periodic-job
 * {@link ScheduledExecutorService}, a virtual-thread {@link ExecutorService} for agent dispatch,
 * and the (empty) {@link RulesEngine}. Beyond mere presence, each executor is given a trivial task
 * to prove the pool actually runs work.
 *
 * <p>{@code classes = Main.class} for the same reason as the other tests in sibling packages: the
 * {@code @SpringBootApplication} lives in {@code com.jobcrm.app} and the default upward package
 * search from a test can't always find it.
 */
@SpringBootTest(classes = Main.class)
class SchedulingConfigTest {

  @Autowired
  @Qualifier("scheduler")
  ScheduledExecutorService scheduler;

  @Autowired
  @Qualifier("agentDispatcher")
  ExecutorService agentDispatcher;

  @Autowired RulesEngine rulesEngine;

  @Test
  void allSchedulingBeansArePresent() {
    assertThat(scheduler).isNotNull();
    assertThat(agentDispatcher).isNotNull();
    assertThat(rulesEngine).isNotNull();
  }

  @Test
  void schedulerRunsAScheduledTask() throws InterruptedException {
    CountDownLatch ran = new CountDownLatch(1);

    scheduler.schedule(ran::countDown, 0, TimeUnit.MILLISECONDS);

    assertThat(ran.await(2, TimeUnit.SECONDS)).as("scheduled task should have run").isTrue();
  }

  @Test
  void agentDispatcherRunsASubmittedTask() throws InterruptedException {
    CountDownLatch ran = new CountDownLatch(1);

    agentDispatcher.execute(ran::countDown);

    assertThat(ran.await(2, TimeUnit.SECONDS)).as("dispatched task should have run").isTrue();
  }

  @Test
  void agentDispatcherRunsOnAVirtualThread() throws InterruptedException {
    CountDownLatch ran = new CountDownLatch(1);
    boolean[] virtual = {false};

    agentDispatcher.execute(
        () -> {
          virtual[0] = Thread.currentThread().isVirtual();
          ran.countDown();
        });

    assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(virtual[0]).as("agent dispatch should use virtual threads").isTrue();
  }

  @Test
  void rulesEngineOnTickIsCallableAndDoesNothing() {
    // The stub must not throw; real rule logic arrives in later tasks.
    rulesEngine.onTick(Instant.parse("2026-01-01T00:00:00Z"));
  }
}
