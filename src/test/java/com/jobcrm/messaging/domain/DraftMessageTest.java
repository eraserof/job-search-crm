package com.jobcrm.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.Channel;
import com.jobcrm.core.domain.ContactId;
import com.jobcrm.core.domain.OpportunityId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DraftMessageTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final OpportunityId OPP = OpportunityId.newId();
  private static final ContactId RECIPIENT = ContactId.newId();

  private static DraftMessage newDraft() {
    return DraftMessage.compose(OPP, RECIPIENT, Channel.EMAIL, "Thanks!", "Hi Marc,\n...\n", T0);
  }

  @Test
  void composed_draft_starts_in_pending_review() {
    DraftMessage d = newDraft();
    assertThat(d.status()).isEqualTo(DraftStatus.PENDING_REVIEW);
    assertThat(d.reviewedAt()).isEmpty();
    assertThat(d.reviewerNote()).isEmpty();
  }

  @Test
  void rejects_blank_body() {
    assertThatThrownBy(
            () -> DraftMessage.compose(OPP, RECIPIENT, Channel.EMAIL, "s", "   \n\t\n", T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void approve_from_pending_transitions_to_approved() {
    DraftMessage d = newDraft();
    d.approve("looks good", T0.plusSeconds(30));
    assertThat(d.status()).isEqualTo(DraftStatus.APPROVED);
    assertThat(d.reviewerNote()).contains("looks good");
    assertThat(d.reviewedAt()).contains(T0.plusSeconds(30));
  }

  @Test
  void discard_from_pending_transitions_to_discarded() {
    DraftMessage d = newDraft();
    d.discard("tone off", T0.plusSeconds(30));
    assertThat(d.status()).isEqualTo(DraftStatus.DISCARDED);
    assertThat(d.reviewerNote()).contains("tone off");
  }

  @Test
  void mark_sent_from_approved_transitions_to_sent() {
    DraftMessage d = newDraft();
    d.approve(null, T0.plusSeconds(30));
    d.markSent();
    assertThat(d.status()).isEqualTo(DraftStatus.SENT);
  }

  @Test
  void mark_sent_from_pending_review_throws() {
    DraftMessage d = newDraft();
    assertThatThrownBy(d::markSent).isInstanceOf(InvalidDraftTransition.class);
  }

  @Test
  void approve_after_discard_throws() {
    DraftMessage d = newDraft();
    d.discard(null, T0);
    assertThatThrownBy(() -> d.approve(null, T0)).isInstanceOf(InvalidDraftTransition.class);
  }

  @Test
  void discard_after_approve_throws() {
    DraftMessage d = newDraft();
    d.approve(null, T0);
    assertThatThrownBy(() -> d.discard(null, T0)).isInstanceOf(InvalidDraftTransition.class);
  }

  @Test
  void mark_sent_from_sent_throws() {
    DraftMessage d = newDraft();
    d.approve(null, T0);
    d.markSent();
    assertThatThrownBy(d::markSent).isInstanceOf(InvalidDraftTransition.class);
  }
}
