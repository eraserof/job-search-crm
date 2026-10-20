package com.jobcrm.agentic.domain;

import java.util.List;
import java.util.Optional;

public record LlmMessage(
    Role role, String content, List<ToolCall> toolCalls, Optional<String> toolCallId) {}
