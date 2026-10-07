package com.jobcrm.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.messaging.domain.DraftMessage;
import com.jobcrm.messaging.domain.DraftMessageRepository;
import com.jobcrm.messaging.domain.DraftStatus;
import com.jobcrm.messaging.domain.InvalidDraftTransition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DraftReviewServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private DraftMessageRepository repo;
  private DraftReviewService service;

  @BeforeEach
  void setUp() {
    repo = Mockito.mock(DraftMessageRepository.class);
    service = new DraftReviewService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static DraftMessage pendingDraft() {
    return DraftMessage.compose(
        OpportunityId.newId(), ContactId.newId(), Channel.EMAIL, "Thanks", "Hi Marc,\n...\n", NOW);
  }

  @Test
  void approve_transitions_pending_to_approved_and_saves() {
    DraftMessage draft = pendingDraft();
    when(repo.findById(draft.id())).thenReturn(Optional.of(draft));

    service.approve(draft.id(), "looks good");

    ArgumentCaptor<DraftMessage> saved = ArgumentCaptor.forClass(DraftMessage.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().status()).isEqualTo(DraftStatus.APPROVED);
    assertThat(saved.getValue().reviewerNote()).contains("looks good");
  }

  @Test
  void markSent_on_pending_draft_throws_invalid_transition_and_does_not_save() {
    DraftMessage draft = pendingDraft();
    when(repo.findById(draft.id())).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> service.markSent(draft.id()))
        .isInstanceOf(InvalidDraftTransition.class);
    verify(repo, never()).save(any());
  }

  @Test
  void approve_unknown_draft_throws() {
    DraftMessageId missing = DraftMessageId.newId();
    when(repo.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.approve(missing, null)).isInstanceOf(UnknownDraft.class);
  }

  @Test
  void listPending_delegates_to_repository() {
    DraftMessage d = pendingDraft();
    when(repo.findPendingReview()).thenReturn(java.util.List.of(d));
    assertThat(service.listPending()).containsExactly(d);
  }
}
