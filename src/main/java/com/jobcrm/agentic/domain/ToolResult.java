package com.jobcrm.agentic.domain;

import java.util.Optional;

/** ToolResult */
public record ToolResult(boolean ok, String resultJson, String errorMessage) {
  public static ToolResult ok(String json) {
    return new ToolResult(true, json, null);
  }

  public static ToolResult failure(String message) {
    return new ToolResult(false, null, message);
  }

  public Optional<String> errorMessageOpt() {
    return Optional.ofNullable(errorMessage);
  }
}
