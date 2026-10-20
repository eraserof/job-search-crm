package com.jobcrm.infrastructure.llm;

import com.jobcrm.agentic.domain.LlmClient;
import com.jobcrm.agentic.domain.LlmRequest;
import com.jobcrm.agentic.domain.LlmResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Test {@link LlmClient} that returns pre-programmed {@link LlmResponse}s in order, one per {@code
 * complete} call. Lets a unit test script a whole multi-turn agent conversation deterministically
 * (turn 1 asks for a tool, turn 2 gives a final answer, etc.) without any network or real model.
 *
 * <p>Also records the requests it received, so a test can assert what the runner actually sent.
 */
public final class MockLlmClient implements LlmClient {

  private final Deque<LlmResponse> programmed = new ArrayDeque<>();
  private final java.util.List<LlmRequest> receivedRequests = new java.util.ArrayList<>();

  /** Queues responses to be returned, in order, by successive {@code complete} calls. */
  public MockLlmClient enqueue(LlmResponse... responses) {
    for (LlmResponse r : responses) {
      programmed.addLast(r);
    }
    return this;
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    receivedRequests.add(request);
    if (programmed.isEmpty()) {
      throw new IllegalStateException(
          "MockLlmClient has no more programmed responses (complete called "
              + (receivedRequests.size())
              + " times)");
    }
    return programmed.removeFirst();
  }

  /** The requests passed to {@code complete}, in call order — for verifying runner behavior. */
  public List<LlmRequest> receivedRequests() {
    return List.copyOf(receivedRequests);
  }

  /** Number of times {@code complete} has been called. */
  public int callCount() {
    return receivedRequests.size();
  }
}
