package com.jobcrm.codegen.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SseParserTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void readsDeltaContent() {
    assertThat(SseParser.content("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}", json))
        .contains("Hel");
  }

  @Test
  void readsNonStreamedMessageContentToo() {
    assertThat(SseParser.content("data: {\"choices\":[{\"message\":{\"content\":\"all\"}}]}", json))
        .contains("all");
  }

  @Test
  void reasoningDeltasAreNotContent() {
    String line = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"}}]}";

    assertThat(SseParser.isData(line)).isTrue();
    assertThat(SseParser.content(line, json)).isEmpty();
  }

  @Test
  void recognisesDoneAndIgnoresNonDataAndMalformedLines() {
    assertThat(SseParser.isDone("data: [DONE]")).isTrue();
    assertThat(SseParser.isDone("data:[DONE]")).isTrue();
    assertThat(SseParser.content("data: [DONE]", json)).isEmpty();
    assertThat(SseParser.content(": keep-alive", json)).isEmpty();
    assertThat(SseParser.content("data: {not json", json)).isEmpty();
  }

  @Test
  void readsErrorEvents() {
    assertThat(SseParser.error("data: {\"error\":{\"message\":\"model not loaded\"}}", json))
        .contains("model not loaded");
    assertThat(SseParser.error("data: {\"error\":\"boom\"}", json)).contains("boom");
    assertThat(SseParser.error("data: {\"choices\":[]}", json)).isEmpty();
  }
}
