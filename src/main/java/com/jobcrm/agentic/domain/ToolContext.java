package com.jobcrm.agentic.domain;

import com.jobcrm.core.domain.company.CompanyRepository;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.event.EventRepository;
import com.jobcrm.core.domain.interaction.InteractionRepository;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.task.TaskRepository;
import com.jobcrm.messaging.domain.DraftMessageRepository;
import java.time.Clock;

public record ToolContext(
    AgentRunId runId,
    AgentName agent,
    Clock clock,
    // Narrow ports the tool is allowed to touch. Each tool declares which
    // fields it reads; the dispatcher constructs contexts accordingly.
    OpportunityRepository opps,
    ContactRepository contacts,
    InteractionRepository interactions,
    TaskRepository tasks,
    DraftMessageRepository drafts,
    EventRepository events,
    CompanyRepository companies) {}
