package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import com.jobcrm.core.domain.interaction.Interaction;
import com.jobcrm.core.domain.interaction.InteractionId;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcInteractionRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private OpportunityId opportunity;
  private ContactId contactA;
  private ContactId contactB;

  private JdbcInteractionRepository repo() {
    return new JdbcInteractionRepository(jdbc());
  }

  @BeforeEach
  void seedParents() {
    UUID companyId = UUID.randomUUID();
    opportunity = new OpportunityId(UUID.randomUUID());
    contactA = new ContactId(UUID.randomUUID());
    contactB = new ContactId(UUID.randomUUID());
    insertCompany(companyId, "Acme", T0.toString());
    insertOpportunity(opportunity.value(), companyId, "Engineer", T0.toString());
    insertContact(contactA.value(), "Contact A", T0.toString());
    insertContact(contactB.value(), "Contact B", T0.toString());
  }

  @Test
  void savesAndFindsByIdWithContacts() {
    Instant occurred = Instant.parse("2026-02-01T10:00:00Z");
    Interaction interaction =
        Interaction.record(
            opportunity,
            List.of(contactA, contactB),
            Channel.EMAIL,
            Direction.INBOUND,
            occurred,
            "thread-123",
            "Hello there",
            "Re: your application",
            "gmail-msg-1");
    repo().save(interaction);

    Optional<Interaction> found = repo().findById(interaction.id());

    assertThat(found).isPresent();
    Interaction i = found.get();
    assertThat(i.id()).isEqualTo(interaction.id());
    assertThat(i.opportunity()).isEqualTo(opportunity);
    assertThat(i.contacts()).containsExactlyInAnyOrder(contactA, contactB);
    assertThat(i.channel()).isEqualTo(Channel.EMAIL);
    assertThat(i.direction()).isEqualTo(Direction.INBOUND);
    assertThat(i.occurredAt()).isEqualTo(occurred);
    assertThat(i.threadKey()).contains("thread-123");
    assertThat(i.bodyText()).isEqualTo("Hello there");
    assertThat(i.subject()).contains("Re: your application");
    assertThat(i.externalId()).contains("gmail-msg-1");
  }

  @Test
  void findByOpportunityReturnsAll() {
    repo().save(newInteraction(Instant.parse("2026-02-01T10:00:00Z"), "e1"));
    repo().save(newInteraction(Instant.parse("2026-02-02T10:00:00Z"), "e2"));

    List<Interaction> found = repo().findByOpportunity(opportunity);
    assertThat(found).hasSize(2);
  }

  @Test
  void findByExternalIdIsIdempotencyLookup() {
    Interaction interaction = newInteraction(Instant.parse("2026-02-01T10:00:00Z"), "ext-99");
    repo().save(interaction);

    Optional<Interaction> found = repo().findByExternalId("ext-99");
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(interaction.id());
  }

  @Test
  void findByThreadKeyReturnsMatches() {
    repo().save(newInteraction(Instant.parse("2026-02-01T10:00:00Z"), "a"));
    List<Interaction> found = repo().findByThreadKey("thread-123");
    assertThat(found).hasSize(1);
  }

  @Test
  void saveTwiceUpdatesRowAndReplacesContacts() {
    Interaction interaction =
        Interaction.record(
            opportunity,
            List.of(contactA, contactB),
            Channel.EMAIL,
            Direction.INBOUND,
            Instant.parse("2026-02-01T10:00:00Z"),
            "thread-123",
            "First body",
            "Subject",
            "ext-1");
    repo().save(interaction);

    Interaction mutated =
        Interaction.reconstitute(
            interaction.id(),
            opportunity,
            List.of(contactA),
            Channel.LINKEDIN,
            Direction.OUTBOUND,
            Instant.parse("2026-02-01T10:00:00Z"),
            "thread-123",
            "Second body",
            "Subject",
            "ext-1");
    repo().save(mutated);

    Optional<Interaction> found = repo().findById(interaction.id());
    assertThat(found).isPresent();
    assertThat(found.get().bodyText()).isEqualTo("Second body");
    assertThat(found.get().channel()).isEqualTo(Channel.LINKEDIN);
    assertThat(found.get().contacts()).containsExactly(contactA);
    assertThat(countRows("interaction")).isEqualTo(1);
    assertThat(countRows("interaction_contact")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(InteractionId.newId())).isEmpty();
  }

  private Interaction newInteraction(Instant occurred, String externalId) {
    return Interaction.record(
        opportunity,
        List.of(contactA),
        Channel.EMAIL,
        Direction.INBOUND,
        occurred,
        "thread-123",
        "Body text",
        "Subject",
        externalId);
  }
}
