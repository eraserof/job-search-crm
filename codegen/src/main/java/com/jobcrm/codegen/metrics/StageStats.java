package com.jobcrm.codegen.metrics;

import java.util.List;

/** Summary of model calls for one stage. */
record StageStats(
    String stage,
    long calls,
    long failures,
    long promptTokensTotal,
    double promptTokensAverage,
    long completionTokensTotal,
    double completionTokensAverage,
    double totalTimeMillisAverage,
    long totalTimeMillisMax,
    double completionTokensPerSecond) {

  StageStats {
    if (stage == null) {
      throw new NullPointerException("stage");
    }
    if (calls < 1L) {
      throw new IllegalArgumentException("calls must be at least 1");
    }
    if (failures < 0L || failures > calls) {
      throw new IllegalArgumentException("failures must be between 0 and calls");
    }
  }

  /**
   * Builds a StageStats from the records for one stage.
   *
   * <ol>
   *   <li>If {@code stage == null}, throw {@code new NullPointerException("stage")}. If {@code
   *       records == null}, throw {@code new NullPointerException("records")}. If {@code
   *       records.isEmpty()} is true, throw {@code new IllegalArgumentException("records must not
   *       be empty")}.
   *   <li>Set {@code long calls = records.size();}.
   *   <li>Set {@code long failures = countFailures(records);}.
   *   <li>Set {@code long promptTokensTotal = sumPromptTokens(records);}.
   *   <li>Set {@code long completionTokensTotal = sumCompletionTokens(records);}.
   *   <li>Set {@code long totalMillisSum = sumTotalMillis(records);}.
   *   <li>Set {@code long totalTimeMillisMax = maxTotalMillis(records);}.
   *   <li>Set {@code double promptTokensAverage = (double) promptTokensTotal / calls;}.
   *   <li>Set {@code double completionTokensAverage = (double) completionTokensTotal / calls;}.
   *   <li>Set {@code double totalTimeMillisAverage = (double) totalMillisSum / calls;}.
   *   <li>If {@code totalMillisSum == 0L}, set {@code double completionTokensPerSecond = 0.0D};
   *       otherwise set {@code double completionTokensPerSecond = ((double) completionTokensTotal *
   *       1000.0D) / totalMillisSum;}.
   *   <li>Return {@code new StageStats(stage, calls, failures, promptTokensTotal,
   *       promptTokensAverage, completionTokensTotal, completionTokensAverage,
   *       totalTimeMillisAverage, totalTimeMillisMax, completionTokensPerSecond);}.
   * </ol>
   *
   * <p>The input list must contain at least one record and is assumed to contain only records for
   * the supplied {@code stage}. The records are assumed to have non-negative token counts and
   * millisecond values, and their sums are assumed not to overflow {@code long}.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code int List.size()}
   * </ul>
   */
  static StageStats from(String stage, List<CallRecord> records) {
    if (stage == null) throw new NullPointerException("stage");
    if (records == null) throw new NullPointerException("records");
    if (records.isEmpty()) throw new IllegalArgumentException("records must not be empty");

    long calls = records.size();
    long failures = countFailures(records);
    long promptTokensTotal = sumPromptTokens(records);
    long completionTokensTotal = sumCompletionTokens(records);
    long totalMillisSum = sumTotalMillis(records);
    long totalTimeMillisMax = maxTotalMillis(records);

    double promptTokensAverage = (double) promptTokensTotal / calls;
    double completionTokensAverage = (double) completionTokensTotal / calls;
    double totalTimeMillisAverage = (double) totalMillisSum / calls;

    double completionTokensPerSecond =
        totalMillisSum == 0L ? 0.0D : ((double) completionTokensTotal * 1000.0D) / totalMillisSum;

    return new StageStats(
        stage,
        calls,
        failures,
        promptTokensTotal,
        promptTokensAverage,
        completionTokensTotal,
        completionTokensAverage,
        totalTimeMillisAverage,
        totalTimeMillisMax,
        completionTokensPerSecond);
  }

  /**
   * Counts records whose success flag is false.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, return {@code 0L}.
   *   <li>Create {@code long failures = 0L;}.
   *   <li>Use {@code for (CallRecord record : records)}; if {@code !record.success()} is true,
   *       increment {@code failures}.
   *   <li>Return {@code failures}.
   * </ol>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code boolean CallRecord.success()}
   * </ul>
   */
  private static long countFailures(List<CallRecord> records) {
    if (records == null) {
      throw new NullPointerException("records");
    }
    if (records.isEmpty()) {
      return 0L;
    }
    long failures = 0L;
    for (CallRecord record : records) {
      if (!record.success()) {
        failures++;
      }
    }
    return failures;
  }

  /**
   * Sums promptTokens across all records.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, return {@code 0L}.
   *   <li>Create {@code long total = 0L;}.
   *   <li>Use {@code for (CallRecord record : records)} and add {@code record.promptTokens()} to
   *       {@code total}.
   *   <li>Return {@code total}.
   * </ol>
   *
   * <p>The sum is assumed not to overflow {@code long}.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code long CallRecord.promptTokens()}
   * </ul>
   */
  private static long sumPromptTokens(List<CallRecord> records) {
    if (records == null) {
      throw new NullPointerException("records");
    }
    if (records.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (CallRecord record : records) {
      total += record.promptTokens();
    }
    return total;
  }

  /**
   * Sums completionTokens across all records.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, return {@code 0L}.
   *   <li>Create {@code long total = 0L;}.
   *   <li>Use {@code for (CallRecord record : records)} and add {@code record.completionTokens()}
   *       to {@code total}.
   *   <li>Return {@code total}.
   * </ol>
   *
   * <p>The sum is assumed not to overflow {@code long}.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code long CallRecord.completionTokens()}
   * </ul>
   */
  private static long sumCompletionTokens(List<CallRecord> records) {
    if (records == null) {
      throw new NullPointerException("records");
    }
    if (records.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (CallRecord record : records) {
      total += record.completionTokens();
    }
    return total;
  }

  /**
   * Sums totalMillis across all records.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, return {@code 0L}.
   *   <li>Create {@code long total = 0L;}.
   *   <li>Use {@code for (CallRecord record : records)} and add {@code record.totalMillis()} to
   *       {@code total}.
   *   <li>Return {@code total}.
   * </ol>
   *
   * <p>The sum is assumed not to overflow {@code long}.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code long CallRecord.totalMillis()}
   * </ul>
   */
  private static long sumTotalMillis(List<CallRecord> records) {
    if (records == null) {
      throw new NullPointerException("records");
    }
    if (records.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (CallRecord record : records) {
      total += record.totalMillis();
    }
    return total;
  }

  /**
   * Returns the maximum totalMillis value in a non-empty list.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, throw {@code new IllegalArgumentException("records
   *       must not be empty")}.
   *   <li>Create {@code long max = records.get(0).totalMillis();}.
   *   <li>Use {@code for (CallRecord record : records)}; if {@code record.totalMillis() > max}, set
   *       {@code max = record.totalMillis();}.
   *   <li>Return {@code max}.
   * </ol>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code CallRecord List.get(int index)}
   *   <li>{@code long CallRecord.totalMillis()}
   * </ul>
   */
  private static long maxTotalMillis(List<CallRecord> records) {
    if (records == null) {
      throw new NullPointerException("records");
    }
    if (records.isEmpty()) {
      throw new IllegalArgumentException("records must not be empty");
    }

    long max = records.get(0).totalMillis();
    for (CallRecord record : records) {
      if (record.totalMillis() > max) {
        max = record.totalMillis();
      }
    }
    return max;
  }
}
