package com.jobcrm.codegen.config;

import java.time.Duration;

/**
 * A stage merged with its platform, with every default applied. This is what the model client needs
 * to make one call.
 */
public record ResolvedStage(
    String name,
    String platformName,
    Platform platform,
    String model,
    double temperature,
    Duration firstTokenTimeout,
    Duration idleTimeout) {}
