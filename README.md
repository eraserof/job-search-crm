# Job-Search CRM

A local, single-user, agentic contact-management application for running a job search end-to-end. Built in Java with domain-driven design; persisted to SQLite; interfaced through a Picocli CLI. All data stays on the machine.

**Status:** Design complete. Implementation not started.

## Design Documents

- [Development guidelines](.kiro/steering/development-guidelines.md) — language, DDD, testing, dependencies
- [Design overview](.kiro/specs/job-search-crm/design.md) — architecture, bounded contexts, flows
- [Domain model](.kiro/specs/job-search-crm/domain-model.md) — aggregates, schema, invariants
- [Agent architecture](.kiro/specs/job-search-crm/agent-architecture.md) — LLM abstraction, tools, runner, roster
- [UI (CLI)](.kiro/specs/job-search-crm/ui-cli.md) — commands, daemon, review flow
- [Task plan](.kiro/specs/job-search-crm/tasks.md) — 32 tasks in 16 execution waves

## What This Is

The central aggregate is the **Opportunity** (one role at one company), not the Contact. A recruiter can appear in several loops; everything of interest (interactions, interviews, follow-ups, drafts) hangs off the loop, not off a person.

The system is a hybrid of deterministic rules and LLM agents. Deterministic work (scheduled Gmail polling, post-interview thank-you creation, stale-loop sweeps) is plain scheduled code. Fuzzy work (classifying an inbound email against opportunities, extracting commitments, drafting replies in the user's voice, answering natural-language questions) is handled by narrow, single-purpose agents with restricted tool sets and capped step counts.

**Drafts are always human-approved before send.** No autonomous outbound communication in v1.

## Not Yet Available

The design is complete but no code exists yet. See `.kiro/specs/job-search-crm/tasks.md` for the ordered implementation plan.
