package com.jobcrm.messaging.application;

import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.messaging.domain.DraftMessage;
import com.jobcrm.messaging.domain.DraftMessageRepository;
import com.jobcrm.messaging.domain.InvalidDraftTransition;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Application service for the draft-review workflow. Single-aggregate transactional facade over
 * {@link DraftMessageRepository}. The draft state machine (PENDING_REVIEW → APPROVED → SENT, or
 * PENDING_REVIEW → DISCARDED) is enforced by the {@link DraftMessage} aggregate; illegal
 * transitions surface as {@link InvalidDraftTransition} (unchecked).
 */
@Service
public class DraftReviewService {

  private final DraftMessageRepository drafts;
  private final Clock clock;

  DraftReviewService(DraftMessageRepository drafts, Clock clock) {
    this.drafts = drafts;
    this.clock = clock;
  }

  /** Drafts awaiting review, oldest first. */
  public List<DraftMessage> listPending() {
    return drafts.findPendingReview();
  }

  /** PENDING_REVIEW → APPROVED. */
  public void approve(DraftMessageId id, String reviewerNote) {
    DraftMessage draft = require(id);
    draft.approve(reviewerNote, clock.instant());
    drafts.save(draft);
  }

  /** PENDING_REVIEW → DISCARDED. */
  public void discard(DraftMessageId id, String reviewerNote) {
    DraftMessage draft = require(id);
    draft.discard(reviewerNote, clock.instant());
    drafts.save(draft);
  }

  /** APPROVED → SENT, called after the user has manually sent the message (v1). */
  public void markSent(DraftMessageId id) {
    DraftMessage draft = require(id);
    draft.markSent();
    drafts.save(draft);
  }

  private DraftMessage require(DraftMessageId id) {
    return drafts.findById(id).orElseThrow(() -> new UnknownDraft(id.value().toString()));
  }
}
