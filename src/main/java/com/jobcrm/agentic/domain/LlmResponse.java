package com.jobcrm.agentic.domain;

import java.util.List;
import java.util.Optional;

public record LlmResponse(
    List<LlmMessage> newMessages, // 1 assistant msg; may contain tool_calls
    List<ToolCall>
        toolCalls, // empty if this is a final answer. The list of tools the llm wants to call.
    // the llm tells us what it wants to call
    Optional<String> finalText, // present when no tool calls. Leaving as optional because there is
    // legitiate reason
    // to have an optional. This is a DTO and not an entity so it should be fine to
    // keep as a Optional
    TokenUsage tokens, //
    String rawProviderResponseJson // captured for AgentTurn audit
    ) {}
