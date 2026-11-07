package com.jobcrm.codegen.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricsLogTest {

  @TempDir Path dir;

  // The tool's own mapper is configured this way (see cli.Tool).
  private final ObjectMapper json =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void appendedRecordsRoundTripInOrderAndParentDirectoriesAreCreated() {
    Path file = dir.resolve("metrics/calls.jsonl");
    MetricsLog log = new MetricsLog(file, json);
    CallRecord first = record("plan", "01-a", true);
    CallRecord second = record("code", null, false);

    log.append(first);
    log.append(second);

    assertThat(file).exists();
    assertThat(log.readAll()).containsExactly(first, second);
  }

  @Test
  void eachRecordIsOneLine() throws Exception {
    Path file = dir.resolve("calls.jsonl");
    MetricsLog log = new MetricsLog(file, json);

    log.append(record("plan", "01-a", true));
    log.append(record("code", "01-a", true));

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith("{").endsWith("}").contains("\"stage\":\"plan\"");
  }

  @Test
  void aMissingFileReadsAsEmpty() {
    assertThat(new MetricsLog(dir.resolve("nope.jsonl"), json).readAll()).isEmpty();
  }

  @Test
  void blankAndUnparsableLinesAreSkipped() throws Exception {
    Path file = dir.resolve("calls.jsonl");
    MetricsLog log = new MetricsLog(file, json);
    log.append(record("plan", "01-a", true));
    // garbage, a blank line, valid JSON that fails CallRecord's validation, and JSON null
    Files.writeString(
        file,
        "not json\n   \n{\"stage\":\"\",\"model\":\"m\",\"platformName\":\"p\",\"time\":\"t\"}\nnull\n",
        java.nio.file.StandardOpenOption.APPEND);
    log.append(record("code", "02-b", true));

    assertThat(log.readAll()).extracting(CallRecord::stage).containsExactly("plan", "code");
  }

  @Test
  void aNullRecordIsRejected() {
    MetricsLog log = new MetricsLog(dir.resolve("calls.jsonl"), json);

    assertThatThrownBy(() -> log.append(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("record must not be null");
  }

  private static CallRecord record(String stage, String job, boolean success) {
    return new CallRecord(
        stage,
        "qwen3.8-27b",
        "brother",
        job,
        3589,
        29179,
        26209,
        1000,
        509000,
        success,
        "2026-11-07T12:49:29Z");
  }
}
