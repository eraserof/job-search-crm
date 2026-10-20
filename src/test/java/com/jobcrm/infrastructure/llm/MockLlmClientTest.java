package com.jobcrm.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jobcrm.agentic.domain.LlmMessage;
import com.jobcrm.agentic.domain.LlmModelParams;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import com.jobcrm.agentic.domain.Role;
import com.jobcrm.agentic.domain.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MockLlmClientTest {

  private static LlmResponse finalAnswer(String text) {
    LlmMessage msg = new LlmMessage(Role.ASSISTANT, text, List.of(), Optional.empty());
    return new LlmResponse(List.of(msg), List.of(), Optional.of(text), new TokenUsage(10, 5), "{}");
  }

  private static LlmRequest anyRequest() {
    return new LlmRequest(
        "system",
        List.of(),
        List.of(),
        new LlmModelParams("m", 0.2, 100, Optional.empty(), Optional.empty()));
  }

  @Test
  void returnsProgrammedResponsesInOrder() {
    MockLlmClient mock = new MockLlmClient().enqueue(finalAnswer("first"), finalAnswer("second"));

    assertThat(mock.complete(anyRequest()).finalText()).contains("first");
    assertThat(mock.complete(anyRequest()).finalText()).contains("second");
    assertThat(mock.callCount()).isEqualTo(2);
  }

  @Test
  void recordsTheRequestsItReceived() {
    MockLlmClient mock = new MockLlmClient().enqueue(finalAnswer("ok"));
    LlmRequest req = anyRequest();

    mock.complete(req);

    assertThat(mock.receivedRequests()).containsExactly(req);
  }

  @Test
  void throwsWhenNoMoreResponsesProgrammed() {
    MockLlmClient mock = new MockLlmClient().enqueue(finalAnswer("only one"));
    mock.complete(anyRequest());

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> mock.complete(anyRequest()));
  }
}
