package com.jobcrm.app;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application {@link Clock}. Injected into application services so "now" is a
 * controllable dependency — production uses the system UTC clock; tests pass a fixed clock for
 * deterministic timestamps.
 */
@Configuration
public class ClockConfig {

  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }
}
