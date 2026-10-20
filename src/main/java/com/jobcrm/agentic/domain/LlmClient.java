package com.jobcrm.agentic.domain;

public interface LlmClient {
  LlmResponse complete(LlmRequest request);
}
