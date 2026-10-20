package com.jobcrm.agentic.domain;

import java.util.List;

public record LlmRequest(
    String systemPrompt,
    List<LlmMessage> history,
    List<ToolSpec> availableTools,
    LlmModelParams params) {}
