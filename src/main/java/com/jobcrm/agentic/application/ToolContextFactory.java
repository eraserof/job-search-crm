package com.jobcrm.agentic.application;

import com.jobcrm.agentic.domain.AgentName;
import com.jobcrm.agentic.domain.AgentRunId;
import com.jobcrm.agentic.domain.ToolContext;
import com.jobcrm.core.domain.company.CompanyRepository;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.event.EventRepository;
import com.jobcrm.core.domain.interaction.InteractionRepository;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.task.TaskRepository;
import com.jobcrm.messaging.domain.DraftMessageRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class ToolContextFactory {
  private final OpportunityRepository opps;
  private final ContactRepository contacts;
  private final InteractionRepository interactions;
  private final TaskRepository tasks;
  private final DraftMessageRepository drafts;
  private final EventRepository events;
  private final CompanyRepository companies;
  private final Clock clock;

  ToolContextFactory(
      Clock clock,
      OpportunityRepository opps,
      ContactRepository contacts,
      InteractionRepository interactions,
      TaskRepository tasks,
      DraftMessageRepository drafts,
      EventRepository events,
      CompanyRepository companies) {
    this.opps = opps;
    this.clock = clock;
    this.contacts = contacts;
    this.interactions = interactions;
    this.tasks = tasks;
    this.drafts = drafts;
    this.events = events;
    this.companies = companies;
  }

  public ToolContext create(AgentRunId runId, AgentName agent) {
    return new ToolContext(
        runId, agent, clock, opps, contacts, interactions, tasks, drafts, events, companies);
  }
}
