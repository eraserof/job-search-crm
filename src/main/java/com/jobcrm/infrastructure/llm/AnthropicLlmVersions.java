package com.jobcrm.infrastructure.llm;

public enum AnthropicLlmVersions {
  LATEST("2023-06-01");

  private final String value;

  AnthropicLlmVersions(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
