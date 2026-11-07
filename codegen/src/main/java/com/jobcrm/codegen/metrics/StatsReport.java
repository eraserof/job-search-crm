package com.jobcrm.codegen.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Plain-text table of stage summaries. */
final class StatsReport {

  private static final String HEADER = "stage calls failures prompt_total prompt_avg completion_total completion_avg avg_ms max_ms ctps";
  private static final String ROW_FORMAT = "%s %d %d %d %.1f %d %.1f %.1f %d %.2f";

  private StatsReport() {}

  /**
   * Returns a plain-text table with one row per stage, sorted by stage name.
   *
   * <ol>
   *   <li>If {@code records == null}, throw {@code new NullPointerException("records")}.
   *   <li>If {@code records.isEmpty()} is true, return the constant {@code HEADER}.
   *   <li>Create
   *       {@code TreeMap<String, List<CallRecord>> groups = new TreeMap<String, List<CallRecord>>();}.
   *   <li>For each {@code CallRecord record} in {@code records}:
   *       <ol type="a">
   *         <li>Set {@code String stage = record.stage();}. If {@code stage == null}, throw
   *             {@code new NullPointerException("stage")}.
   *         <li>Set {@code List<CallRecord> bucket = groups.get(stage);}. If
   *             {@code bucket == null}, set {@code bucket = new ArrayList<CallRecord>();} and call
   *             {@code groups.put(stage, bucket);}.
   *         <li>Call {@code bucket.add(record);}.
   *       </ol>
   *   <li>Create {@code StringBuilder out = new StringBuilder(HEADER);}. Call
   *       {@code out.append('\n');}.
   *   <li>For each {@code String stage} in {@code groups.keySet()}: set
   *       {@code StageStats stats = StageStats.from(stage, groups.get(stage));}, call
   *       {@code out.append(formatRow(stats));}, then call {@code out.append('\n');}.
   *   <li>Return {@code out.toString();}.
   * </ol>
   *
   * <p>The header line is exactly:
   * <pre>
   * stage calls failures prompt_total prompt_avg completion_total completion_avg avg_ms max_ms ctps
   * </pre>
   *
   * <p>An empty non-null list returns only that header line, with no trailing newline. A non-empty
   * result has one row per stage and ends with a newline after the last row. Stage names are sorted
   * in ascending {@code String} order by {@code TreeMap}.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code String CallRecord.stage()}
   *   <li>{@code boolean List.isEmpty()}
   *   <li>{@code new TreeMap<String, List<CallRecord>>()}
   *   <li>{@code List<CallRecord> TreeMap.get(Object key)}
   *   <li>{@code List<CallRecord> TreeMap.put(String key, List<CallRecord> value)}
   *   <li>{@code java.util.Set<String> TreeMap.keySet()}
   *   <li>{@code new ArrayList<CallRecord>()}
   *   <li>{@code boolean List.add(CallRecord element)}
   *   <li>{@code StringBuilder(String str)}
   *   <li>{@code StringBuilder StringBuilder.append(char c)}
   *   <li>{@code StringBuilder StringBuilder.append(CharSequence csq)}
   *   <li>{@code String StringBuilder.toString()}
   *   <li>{@code StageStats StageStats.from(String stage, java.util.List<CallRecord> records)}
   * </ul>
   */
  static String report(List<CallRecord> records) {
  if (records == null) throw new NullPointerException("records");
  if (records.isEmpty()) return HEADER;

  TreeMap<String, List<CallRecord>> groups = new TreeMap<>();
  for (CallRecord record : records) {
    String stage = record.stage();
    if (stage == null) throw new NullPointerException("stage");
    List<CallRecord> bucket = groups.get(stage);
    if (bucket == null) {
      bucket = new ArrayList<>();
      groups.put(stage, bucket);
    }
    bucket.add(record);
  }

  StringBuilder out = new StringBuilder(HEADER);
  out.append('\n');
  for (String stage : groups.keySet()) {
    StageStats stats = StageStats.from(stage, groups.get(stage));
    out.append(formatRow(stats)).append('\n');
  }

  return out.toString();
  }

  /**
   * Formats one StageStats as a table row.
   *
   * <ol>
   *   <li>If {@code stats == null}, throw {@code new NullPointerException("stats")}.
   *   <li>Return
   *       {@code String.format(Locale.US, ROW_FORMAT, stats.stage(), stats.calls(),
   *       stats.failures(), stats.promptTokensTotal(), stats.promptTokensAverage(),
   *       stats.completionTokensTotal(), stats.completionTokensAverage(),
   *       stats.totalTimeMillisAverage(), stats.totalTimeMillisMax(),
   *       stats.completionTokensPerSecond());}.
   * </ol>
   *
   * <p>The row format is exactly:
   * <pre>
   * %s %d %d %d %.1f %d %.1f %.1f %d %.2f
   * </pre>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code String String.format(Locale locale, String format, Object... args)}
   *   <li>{@code java.util.Locale.US}
   *   <li>{@code String StageStats.stage()}
   *   <li>{@code long StageStats.calls()}
   *   <li>{@code long StageStats.failures()}
   *   <li>{@code long StageStats.promptTokensTotal()}
   *   <li>{@code double StageStats.promptTokensAverage()}
   *   <li>{@code long StageStats.completionTokensTotal()}
   *   <li>{@code double StageStats.completionTokensAverage()}
   *   <li>{@code double StageStats.totalTimeMillisAverage()}
   *   <li>{@code long StageStats.totalTimeMillisMax()}
   *   <li>{@code double StageStats.completionTokensPerSecond()}
   * </ul>
   */
  private static String formatRow(StageStats stats) {
  if (stats == null) {
    throw new NullPointerException("stats");
  }
  return String.format(Locale.US, ROW_FORMAT, stats.stage(), stats.calls(), stats.failures(), stats.promptTokensTotal(), stats.promptTokensAverage(), stats.completionTokensTotal(), stats.completionTokensAverage(), stats.totalTimeMillisAverage(), stats.totalTimeMillisMax(), stats.completionTokensPerSecond());
  }
}
