package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.shared.DraftMessageId;
import com.jobcrm.messaging.domain.DraftMessage;
import com.jobcrm.messaging.domain.DraftStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcDraftMessageRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private OpportunityId opportunity;
  private ContactId recipient;

  private JdbcDraftMessageRepository repo() {
    return new JdbcDraftMessageRepository(jdbc());
  }

  @BeforeEach
  void seedParents() {
    UUID companyId = UUID.randomUUID();
    opportunity = new OpportunityId(UUID.randomUUID());
    recipient = new ContactId(UUID.randomUUID());
    insertCompany(companyId, "Acme", T0.toString());
    insertOpportunity(opportunity.value(), companyId, "Engineer", T0.toString());
    insertContact(recipient.value(), "Recipient", T0.toString());
  }

  @Test
  void savesAndFindsByIdWithAllFields() {
    DraftMessage draft =
        DraftMessage.compose(
            opportunity, recipient, Channel.EMAIL, "Thanks!", "Thank you for the chat.", T0);
    repo().save(draft);

    Optional<DraftMessage> found = repo().findById(draft.id());

    assertThat(found).isPresent();
    DraftMessage d = found.get();
    assertThat(d.id()).isEqualTo(draft.id());
    assertThat(d.opportunity()).isEqualTo(opportunity);
    assertThat(d.recipient()).isEqualTo(recipient);
    assertThat(d.channel()).isEqualTo(Channel.EMAIL);
    assertThat(d.subject()).isEqualTo("Thanks!");
    assertThat(d.body()).isEqualTo("Thank you for the chat.");
    assertThat(d.status()).isEqualTo(DraftStatus.PENDING_REVIEW);
    assertThat(d.createdAt()).isEqualTo(T0);
    assertThat(d.reviewedAt()).isEmpty();
    assertThat(d.reviewerNote()).isEmpty();
  }

  @Test
  void findPendingReviewReturnsOnlyPendingInCreationOrder() {
    DraftMessage first = newDraft("first body", T0);
    DraftMessage second = newDraft("second body", T0.plusSeconds(60));
    DraftMessage approved = newDraft("approved body", T0.plusSeconds(120));
    approved.approve("looks good", T0.plusSeconds(180));

    repo().save(second);
    repo().save(first);
    repo().save(approved);

    List<DraftMessage> pending = repo().findPendingReview();
    assertThat(pending).extracting(DraftMessage::id).containsExactly(first.id(), second.id());
  }

  @Test
  void findByOpportunityReturnsAll() {
    repo().save(newDraft("a", T0));
    repo().save(newDraft("b", T0.plusSeconds(60)));

    assertThat(repo().findByOpportunity(opportunity)).hasSize(2);
  }

  @Test
  void saveTwiceUpdatesInPlacePreservingReviewFields() {
    DraftMessage draft = newDraft("body text", T0);
    repo().save(draft);

    draft.approve("approved note", T0.plusSeconds(300));
    repo().save(draft);

    Optional<DraftMessage> found = repo().findById(draft.id());
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(DraftStatus.APPROVED);
    assertThat(found.get().reviewerNote()).contains("approved note");
    assertThat(found.get().reviewedAt()).contains(T0.plusSeconds(300));
    assertThat(countRows("draft_message")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(DraftMessageId.newId())).isEmpty();
  }

  private DraftMessage newDraft(String body, Instant createdAt) {
    return DraftMessage.compose(opportunity, recipient, Channel.EMAIL, "Subject", body, createdAt);
  }
}
