package com.jobcrm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.event.Event;
import com.jobcrm.core.domain.event.EventKind;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import com.jobcrm.core.domain.interaction.Interaction;
import com.jobcrm.core.domain.opportunity.ContactRef;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.opportunity.Stage;
import com.jobcrm.core.domain.task.Task;
import com.jobcrm.core.domain.task.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcOpportunityRepositoryTest extends AbstractRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private CompanyId company;
  private ContactId contactA;
  private ContactId contactB;

  private JdbcOpportunityRepository repo() {
    return new JdbcOpportunityRepository(jdbc());
  }

  @BeforeEach
  void seedParents() {
    company = new CompanyId(UUID.randomUUID());
    contactA = new ContactId(UUID.randomUUID());
    contactB = new ContactId(UUID.randomUUID());
    insertCompany(company.value(), "Acme", T0.toString());
    insertContact(contactA.value(), "Contact A", T0.toString());
    insertContact(contactB.value(), "Contact B", T0.toString());
  }

  @Test
  void savesAndFindsByIdWithContactsAndDerivedLists() {
    Opportunity opp = Opportunity.create(company, "Staff Engineer", T0);
    opp.attachContact(contactA, ContactRole.RECRUITER_INTERNAL, T0);
    opp.attachContact(contactB, ContactRole.HIRING_MANAGER, T0);
    repo().save(opp);

    // Persist a child interaction, event, and task in their own tables.
    Instant occurred = Instant.parse("2026-02-10T12:00:00Z");
    Interaction interaction =
        Interaction.record(
            opp.id(),
            List.of(contactA),
            Channel.EMAIL,
            Direction.INBOUND,
            occurred,
            "thread-1",
            "body",
            "subject",
            "ext-1");
    new JdbcInteractionRepository(jdbc()).save(interaction);

    Event event =
        Event.schedule(
            opp.id(),
            EventKind.INTERVIEW,
            Instant.parse("2026-02-11T15:00:00Z"),
            Instant.parse("2026-02-11T16:00:00Z"),
            List.of(contactA),
            "cal-1",
            "Interview",
            null);
    new JdbcEventRepository(jdbc()).save(event);

    Task openTask =
        Task.create(opp.id(), TaskType.NUDGE, Instant.parse("2026-02-20T00:00:00Z"), T0, null);
    Task doneTask =
        Task.create(opp.id(), TaskType.RESPOND, Instant.parse("2026-02-20T00:00:00Z"), T0, null);
    doneTask.markDone(null, T0.plusSeconds(60));
    new JdbcTaskRepository(jdbc()).save(openTask);
    new JdbcTaskRepository(jdbc()).save(doneTask);

    Optional<Opportunity> found = repo().findById(opp.id());

    assertThat(found).isPresent();
    Opportunity o = found.get();
    assertThat(o.id()).isEqualTo(opp.id());
    assertThat(o.companyId()).isEqualTo(company);
    assertThat(o.role()).isEqualTo("Staff Engineer");
    assertThat(o.stage()).isEqualTo(Stage.APPLIED);
    assertThat(o.contacts())
        .containsExactlyInAnyOrder(
            new ContactRef(contactA, ContactRole.RECRUITER_INTERNAL),
            new ContactRef(contactB, ContactRole.HIRING_MANAGER));
    assertThat(o.interactions()).containsExactly(interaction.id());
    assertThat(o.events()).containsExactly(event.id());
    assertThat(o.openTasks()).containsExactly(openTask.id());
    assertThat(o.createdAt()).isEqualTo(T0);
    // lastInteractionAt derived from MAX(occurred_at).
    assertThat(o.lastInteractionAt()).isEqualTo(occurred);
  }

  @Test
  void lastInteractionAtFallsBackToCreatedAtWhenNoInteractions() {
    Opportunity opp = Opportunity.create(company, "Engineer", T0);
    repo().save(opp);

    Optional<Opportunity> found = repo().findById(opp.id());
    assertThat(found).isPresent();
    assertThat(found.get().lastInteractionAt()).isEqualTo(T0);
  }

  @Test
  void findByCompanyFilters() {
    Opportunity opp = Opportunity.create(company, "Engineer", T0);
    repo().save(opp);

    CompanyId other = new CompanyId(UUID.randomUUID());
    insertCompany(other.value(), "Other", T0.toString());
    Opportunity otherOpp = Opportunity.create(other, "Designer", T0);
    repo().save(otherOpp);

    List<Opportunity> found = repo().findByCompany(company);
    assertThat(found).extracting(Opportunity::id).containsExactly(opp.id());
  }

  @Test
  void findByStageFilters() {
    Opportunity applied = Opportunity.create(company, "Applied Role", T0);
    repo().save(applied);

    Opportunity screening = Opportunity.create(company, "Screening Role", T0);
    screening.advanceStage(Stage.RECRUITER_SCREEN, T0.plusSeconds(60));
    repo().save(screening);

    List<Opportunity> found = repo().findByStage(Stage.RECRUITER_SCREEN);
    assertThat(found).extracting(Opportunity::id).containsExactly(screening.id());
  }

  @Test
  void searchByTextMatchesRoleAndRespectsLimit() {
    repo().save(Opportunity.create(company, "Backend Engineer", T0));
    repo().save(Opportunity.create(company, "Backend Architect", T0));
    repo().save(Opportunity.create(company, "Product Manager", T0));

    List<Opportunity> all = repo().searchByText("backend", 10);
    assertThat(all).extracting(Opportunity::role).contains("Backend Engineer", "Backend Architect");
    assertThat(all).extracting(Opportunity::role).doesNotContain("Product Manager");

    assertThat(repo().searchByText("backend", 1)).hasSize(1);
  }

  @Test
  void findSilentSinceReturnsNonTerminalOppsStaleAtThreshold() {
    // Silent opp: no interactions, created at T0, so lastInteractionAt = T0.
    Opportunity silent = Opportunity.create(company, "Silent Role", T0);
    repo().save(silent);

    // Recently-active opp: has an interaction after the threshold.
    Opportunity active = Opportunity.create(company, "Active Role", T0);
    active.attachContact(contactA, ContactRole.RECRUITER_INTERNAL, T0);
    repo().save(active);
    Interaction recent =
        Interaction.record(
            active.id(),
            List.of(contactA),
            Channel.EMAIL,
            Direction.INBOUND,
            Instant.parse("2026-06-01T00:00:00Z"),
            "t",
            "b",
            "s",
            "ext-active");
    new JdbcInteractionRepository(jdbc()).save(recent);

    // Terminal (withdrawn) opp that is also stale — must be excluded.
    Opportunity withdrawn = Opportunity.create(company, "Withdrawn Role", T0);
    withdrawn.advanceStage(Stage.WITHDRAWN, T0.plusSeconds(60));
    repo().save(withdrawn);

    List<Opportunity> found = repo().findSilentSince(Instant.parse("2026-03-01T00:00:00Z"));
    assertThat(found).extracting(Opportunity::id).containsExactly(silent.id());
  }

  @Test
  void saveTwiceUpdatesRowAndReplacesContacts() {
    Opportunity opp = Opportunity.create(company, "Engineer", T0);
    opp.attachContact(contactA, ContactRole.RECRUITER_INTERNAL, T0);
    opp.attachContact(contactB, ContactRole.HIRING_MANAGER, T0);
    repo().save(opp);

    opp.advanceStage(Stage.RECRUITER_SCREEN, T0.plusSeconds(60));
    opp.detachContact(contactB, T0.plusSeconds(60));
    repo().save(opp);

    Optional<Opportunity> found = repo().findById(opp.id());
    assertThat(found).isPresent();
    assertThat(found.get().stage()).isEqualTo(Stage.RECRUITER_SCREEN);
    assertThat(found.get().contacts())
        .containsExactly(new ContactRef(contactA, ContactRole.RECRUITER_INTERNAL));
    assertThat(countRows("opportunity")).isEqualTo(1);
    assertThat(countRows("opportunity_contact")).isEqualTo(1);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(repo().findById(OpportunityId.newId())).isEmpty();
  }
}
