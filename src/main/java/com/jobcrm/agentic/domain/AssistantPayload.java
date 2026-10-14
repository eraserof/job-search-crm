package com.jobcrm.agentic.domain;

import java.util.List;

public record AssistantPayload(String contentJson, List<ToolCall> toolCalls) {}
