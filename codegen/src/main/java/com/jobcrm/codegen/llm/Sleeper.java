package com.jobcrm.codegen.llm;

import java.time.Duration;

/** Waits. Abstracted so retry tests do not actually sleep. */
@FunctionalInterface
public interface Sleeper {

  void sleep(Duration duration) throws InterruptedException;

  /** The real one: {@code Thread.sleep(duration)}. */
  static Sleeper system() {
    return Thread::sleep;
  }
}
