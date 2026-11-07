package com.jobcrm.codegen.metrics;

/**
 * One model call, as stored in the metrics log.
 *
 * @param job optional job name; may be null
 */
public record CallRecord(
    String stage,
    String model,
    String platformName,
    String job,
    long promptTokens,
    long completionTokens,
    long reasoningTokens,
    long timeToFirstTokenMillis,
    long totalMillis,
    boolean success,
    String time) {

  public CallRecord {
    if (stage == null || stage.isBlank()) {
      throw new IllegalArgumentException("stage must not be blank");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }
    if (platformName == null || platformName.isBlank()) {
      throw new IllegalArgumentException("platformName must not be blank");
    }
    if (time == null || time.isBlank()) {
      throw new IllegalArgumentException("time must not be blank");
    }
    if (promptTokens < 0) {
      throw new IllegalArgumentException("promptTokens must not be negative");
    }
    if (completionTokens < 0) {
      throw new IllegalArgumentException("completionTokens must not be negative");
    }
    if (reasoningTokens < 0) {
      throw new IllegalArgumentException("reasoningTokens must not be negative");
    }
    if (timeToFirstTokenMillis < 0) {
      throw new IllegalArgumentException("timeToFirstTokenMillis must not be negative");
    }
    if (totalMillis < 0) {
      throw new IllegalArgumentException("totalMillis must not be negative");
    }
  }
}
