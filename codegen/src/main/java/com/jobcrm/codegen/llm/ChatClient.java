package com.jobcrm.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcrm.codegen.config.ResolvedStage;

/**
 * Sends ONE fresh single-user-message chat request and returns the assistant's text. No history
 * is kept between calls: every call is a clean slate.
 */
public interface ChatClient {

  /**
   * @param stage where and how to call (platform, model, temperature, timeouts)
   * @param prompt the whole prompt, sent as the only user message
   * @param responseFormat nullable OpenAI {@code response_format} object, sent as-is when non-null
   * @return the concatenated assistant content (may be empty)
   * @throws LlmException on transport errors, non-200 responses, timeouts, or stalls
   */
  String complete(ResolvedStage stage, String prompt, JsonNode responseFormat) throws LlmException;
}
