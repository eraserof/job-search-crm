package com.jobcrm.agentic.domain;

import java.time.Instant;

public record RunRow(
    AgentRunId id,
    AgentName agent,
    AgentRunStatus status,
    Instant startedAt,
    Instant finishedAt,
    String inputJson,
    String finalOutput,
    TokenUsage tokens,
    String errorMessage) {}
