package com.jobcrm.infrastructure.llm;

import com.jobcrm.agentic.domain.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class LlmClientFactory {
  private final LlmClient anthropicClient;

  // mock/recorded are test-time — see note below

  LlmClientFactory(AnthropicLlmClient anthropicClient) {
    this.anthropicClient = anthropicClient;
  }

  public LlmClient forProvider(String provider) {
    return switch (provider) {
      case "anthropic" -> anthropicClient;
      default -> throw new LlmClientException("Unknown LLM provider: " + provider);
    };
  }
}
