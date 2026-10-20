package com.jobcrm.agentic.domain;

import java.util.Optional;

public record LlmModelParams(
    String modelId,
    double temperature,
    int maxTokens,
    Optional<Integer> topK,
    Optional<Double> topP) {}
