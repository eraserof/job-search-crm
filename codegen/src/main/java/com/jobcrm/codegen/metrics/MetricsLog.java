package com.jobcrm.codegen.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Appends {@link CallRecord} values to a JSON Lines file and reads them back. */
public final class MetricsLog {

  private final Path file;
  private final ObjectMapper json;

  public MetricsLog(Path file, ObjectMapper json) {
    this.file = file;
    this.json = json;
  }

  /**
   * Appends one record as a single UTF-8 JSON line to the log file, creating parent directories if
   * needed.
   *
   * <ol>
   *   <li>If {@code record} is null, throw new IllegalArgumentException("record must not be null").
   *   <li>Let {@code Path parent = file.getParent()}; if {@code parent != null}, call {@code
   *       Files.createDirectories(parent)}.
   *   <li>Call {@code Files.writeString(file, json.writeValueAsString(record) + "\n",
   *       StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
   *       StandardOpenOption.WRITE);}.
   * </ol>
   *
   * <p>If an IOException occurs in step 2 or step 3, throw new UncheckedIOException("cannot append
   * metrics to " + file, e). The ObjectMapper is assumed to write compact JSON.
   *
   * <pre>
   * "record must not be null"
   * "\n"
   * "cannot append metrics to "
   * </pre>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code Path Path.getParent()}
   *   <li>{@code void Files.createDirectories(Path dir) throws IOException}
   *   <li>{@code String ObjectMapper.writeValueAsString(Object value)}
   *   <li>{@code Charset StandardCharsets.UTF_8}
   *   <li>{@code void Files.writeString(Path path, CharSequence csq, Charset charset, OpenOption...
   *       options) throws IOException}
   *   <li>{@code StandardOpenOption CREATE}
   *   <li>{@code StandardOpenOption APPEND}
   *   <li>{@code StandardOpenOption WRITE}
   *   <li>{@code IllegalArgumentException(String message)}
   *   <li>{@code UncheckedIOException(String message, Throwable cause)}
   * </ul>
   */
  public void append(CallRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("record must not be null");
    }
    Path parent = file.getParent();
    if (parent != null) {
      try {
        Files.createDirectories(parent);
      } catch (IOException e) {
        throw new UncheckedIOException("cannot append metrics to " + file, e);
      }
    }
    try {
      Files.writeString(
          file,
          json.writeValueAsString(record) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot append metrics to " + file, e);
    }
  }

  /**
   * Reads all parsable CallRecord lines from the log file, skipping blank and unparsable lines.
   *
   * <ol>
   *   <li>If {@code !Files.exists(file)}, return a new empty {@code ArrayList<CallRecord>}.
   *   <li>Try to call {@code List<String> lines = Files.readAllLines(file,
   *       StandardCharsets.UTF_8);}; if an IOException occurs, throw new
   *       UncheckedIOException("cannot read metrics from " + file, e).
   *   <li>Create {@code ArrayList<CallRecord> records = new ArrayList<>();}.
   *   <li>For each {@code String raw} in {@code lines}: let {@code String line = raw.trim();}; if
   *       {@code line.isEmpty()}, continue to the next line.
   *   <li>Try to call {@code CallRecord parsed = json.readValue(line, CallRecord.class);}; if
   *       {@code parsed != null}, call {@code records.add(parsed);}. If a RuntimeException or
   *       IOException occurs, skip this line and continue with the next one.
   *   <li>Return {@code records}.
   * </ol>
   *
   * <p>If the file does not exist, return an empty list. Blank or whitespace-only lines are
   * skipped. Lines that do not parse into a valid CallRecord are skipped without throwing. If
   * reading the file fails with IOException, throw UncheckedIOException with message "cannot read
   * metrics from " + file.
   *
   * <pre>
   * "cannot read metrics from "
   * </pre>
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>{@code boolean Files.exists(Path path)}
   *   <li>{@code List<String> Files.readAllLines(Path path, Charset charset) throws IOException}
   *   <li>{@code String String.trim()}
   *   <li>{@code boolean String.isEmpty()}
   *   <li>{@code <T> T ObjectMapper.readValue(String content, Class<T> valueType)}
   *   <li>{@code ArrayList()}
   *   <li>{@code void List.add(E e)}
   *   <li>{@code Charset StandardCharsets.UTF_8}
   *   <li>{@code UncheckedIOException(String message, Throwable cause)}
   * </ul>
   */
  public List<CallRecord> readAll() {
    if (!Files.exists(file)) {
      return new ArrayList<>();
    }
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      ArrayList<CallRecord> records = new ArrayList<>();
      for (String raw : lines) {
        String line = raw.trim();
        if (line.isEmpty()) {
          continue;
        }
        try {
          CallRecord parsed = json.readValue(line, CallRecord.class);
          if (parsed != null) {
            records.add(parsed);
          }
        } catch (RuntimeException | IOException e) {
          // Skip this line and continue with the next one
        }
      }
      return records;
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read metrics from " + file, e);
    }
  }
}
