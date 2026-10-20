package com.jobcrm.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrm.agentic.domain.LlmModelParams;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordedLlmClientTest {

  private final ObjectMapper json = new ObjectMapper();

  private static LlmRequest anyRequest() {
    return new LlmRequest(
        "system",
        List.of(),
        List.of(),
        new LlmModelParams("m", 0.2, 100, Optional.empty(), Optional.empty()));
  }

  private static final String TWO_TURN_FIXTURE =
      """
      [
        { "text": "let me look",
          "toolCalls": [ { "id": "t1", "name": "readOpportunity", "argsJson": "{\\"id\\":\\"opp-1\\"}" } ],
          "inputTokens": 100, "outputTokens": 20 },
        { "text": "here is the answer", "toolCalls": [], "inputTokens": 120, "outputTokens": 40 }
      ]
      """;

  @Test
  void replaysRecordedTurnsInOrder() {
    RecordedLlmClient client = RecordedLlmClient.fromFixture(TWO_TURN_FIXTURE, json);

    LlmResponse first = client.complete(anyRequest());
    assertThat(first.toolCalls()).hasSize(1);
    assertThat(first.toolCalls().get(0).name()).isEqualTo("readOpportunity");
    assertThat(first.finalText()).isEmpty();
    assertThat(first.tokens()).isEqualTo(new com.jobcrm.agentic.domain.TokenUsage(100, 20));

    LlmResponse second = client.complete(anyRequest());
    assertThat(second.toolCalls()).isEmpty();
    assertThat(second.finalText()).contains("here is the answer");
  }

  @Test
  void throwsWhenFixtureIsExhausted() {
    RecordedLlmClient client = RecordedLlmClient.fromFixture(TWO_TURN_FIXTURE, json);
    client.complete(anyRequest());
    client.complete(anyRequest());

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> client.complete(anyRequest()));
  }

  @Test
  void rejectsNonArrayFixture() {
    assertThatExceptionOfType(LlmJsonException.class)
        .isThrownBy(() -> RecordedLlmClient.fromFixture("{\"not\":\"an array\"}", json));
  }
}
