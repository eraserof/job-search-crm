# UI: Command-Line Interface

The v1 user surface is a Picocli-based CLI. No web UI. No desktop app. Everything a user needs to do is a `jobcrm` subcommand or happens inside the daemon that runs the schedulers. This document defines the command surface, the daemon lifecycle, output conventions, and the review flow. Domain concepts are in `domain-model.md`; agent concepts are in `agent-architecture.md`.

---

## Design Goals

- **One binary, many commands.** `jobcrm <verb> [args]` for everything.
- **One-shot commands over long-running commands.** Each subcommand does its thing and exits, except for the daemon.
- **Machine-readable output on demand.** Every list/read command supports `--json`. Human output is the default: coloured tables when the terminal supports them, plain text otherwise.
- **Non-interactive by default.** No prompts unless a command is explicitly interactive (`draft edit`, `auth gmail`). Everything else takes flags/positional args.
- **Fail loud, fail helpfully.** Errors print a message plus an `agentRunId` (when relevant) and a hint about the next command to run.

---

## Daemon Model

Two runtime modes:

1. **Daemon mode** — `jobcrm daemon start`. Runs the Spring Boot process indefinitely, hosting:
   - Gmail poller (every 5 minutes).
   - Calendar sync (every 5 minutes).
   - Nightly stale-loop sweep (03:00 local).
   - Post-interview follow-up rule (fires on calendar-sync detection of a just-ended interview).
   - Daily briefing generator (07:00 local; result available via `jobcrm today`).

2. **One-shot mode** — every other command starts the Spring context, does one unit of work, and exits.

Both modes share the same database file. The daemon takes a filesystem lock on `~/.jobcrm/daemon.lock`; one-shot commands do not, but they respect the write serialization inherent in SQLite + the size-1 write pool. When the daemon is running, one-shot commands still work — they compete for the write pool but that's fine at the scales this app operates at.

### Daemon lifecycle commands

```
jobcrm daemon start            Start the daemon (foreground). Optionally --detach.
jobcrm daemon stop             Signal a running daemon to shut down.
jobcrm daemon status           Report whether the daemon is running, and its uptime.
jobcrm daemon logs [--tail N]  Tail the daemon log file.
```

On Windows, `--detach` uses `START /B` semantics via a helper; a proper Windows Service wrapper is out of scope for v1.

---

## Global Flags

Every command accepts:

| Flag | Meaning |
|------|---------|
| `--json` | Emit machine-readable JSON to stdout. |
| `--quiet`, `-q` | Suppress non-essential output. |
| `--verbose`, `-v` | Debug output (equivalent to log level DEBUG for the command). |
| `--config <path>` | Override the config-file location. |
| `--help`, `-h` | Show usage. |

---

## Command Surface

### Setup and auth

```
jobcrm init                                   Create the config directory, config.yml, and DB.
jobcrm config get <key>                       Read a config value.
jobcrm config set <key> <value>               Write a config value to the user config file.
jobcrm auth gmail                             Run the Gmail OAuth device flow, store the refresh token.
jobcrm auth gmail --revoke                    Delete the stored Gmail refresh token.
jobcrm auth llm --set-key <apiKey>            Store the LLM provider API key.
jobcrm auth status                            Report which integrations are authenticated.
```

`jobcrm init` is the first command a new user runs. It bootstraps `~/.jobcrm/config.yml` with sensible defaults, creates the SQLite file, and runs Flyway migrations.

### Opportunities

```
jobcrm opp new                                Create an Opportunity (prompts for company + role).
jobcrm opp new --company <name> --role <r>    Non-interactive create.
jobcrm opp list [--stage <s>] [--company <c>] List opportunities, optionally filtered.
jobcrm opp show <opportunityId|shortRef>      Show full detail: contacts, latest interactions, open tasks.
jobcrm opp advance <ref> --to <stage>         Advance stage; fails on illegal transitions.
jobcrm opp attach-contact <ref> <contactRef>  Attach a contact with an inline role prompt.
jobcrm opp attach-resume <ref> <path>         Attach a resume Document to the opportunity.
```

**Short refs**: every entity supports a short reference (first 8 chars of UUID, or a slug like `stripe-be-2026-09`). The `--json` variant returns the canonical UUID.

### Contacts

```
jobcrm contact new                            Interactive.
jobcrm contact new --name --email [...]       Non-interactive.
jobcrm contact list [--company <c>] [--query <q>]
jobcrm contact show <ref>
jobcrm contact rename <ref> --to "<new name>"
jobcrm contact add-email <ref> <email>
```

### Companies

```
jobcrm company new --name <n> [--domain <d>]
jobcrm company list
jobcrm company show <ref>
jobcrm company set-domain <ref> <domain>
```

### Interactions

Ingestion for Gmail is automatic via the daemon. For call notes and LinkedIn threads, the user pastes.

```
jobcrm log call <opportunityRef> [--with <contactRef>...] [--at <ISO-instant>]
    Prompts for the body via $EDITOR (or `--body -` reads stdin, `--body <text>` inline).
    Dispatches CallNoteIngestAgent.

jobcrm log linkedin [--opportunity <ref>]
    Prompts for the pasted thread via $EDITOR or stdin.
    Dispatches LinkedInIngestAgent. Opportunity binding may be resolved by the agent
    from the paste; --opportunity forces the binding.

jobcrm sync-gmail
    One-shot: pull new email from Gmail and dispatch EmailIngestAgent for each new message.
    Same code path the daemon uses; safe to run while the daemon is up (mutex-protected).

jobcrm interactions <opportunityRef> [--since <date>] [--limit N]
    List interactions for an opportunity.
```

### Tasks and drafts

```
jobcrm today                                  Show what's due today: tasks, upcoming interviews,
                                              pending drafts to review, silent loops to nudge.

jobcrm tasks list [--open|--done] [--due-by <date>] [--opportunity <ref>]
jobcrm tasks done <taskRef> [--note "..."]
jobcrm tasks dismiss <taskRef> [--note "..."]

jobcrm drafts list [--pending|--approved|--all]
jobcrm drafts show <draftRef>                 Print the full draft.
jobcrm drafts edit <draftRef>                 Open the draft body in $EDITOR; save-writes back.
jobcrm drafts approve <draftRef> [--note ...]
jobcrm drafts discard <draftRef> [--note ...]
jobcrm drafts mark-sent <draftRef>            User confirms after sending from Gmail manually.

jobcrm draft <taskRef>                        Dispatch DraftReplyAgent for a task on demand.
```

The **review flow** is entirely `drafts show → drafts edit → drafts approve → user sends in Gmail → drafts mark-sent`. No send tooling in v1.

### Query and prep

```
jobcrm ask "<question>"                       Dispatch QueryAgent; print the grounded answer.
jobcrm prep <opportunityRef>                  Dispatch InterviewPrepAgent; print the briefing.
jobcrm status <opportunityRef>                Non-agent, fast: prints stage, contacts, last interaction,
                                              open tasks. Deterministic; no LLM call.
```

### Events

```
jobcrm event list [--opportunity <ref>] [--upcoming]
jobcrm event show <eventRef>
jobcrm event note <eventRef> --body "..."     Attach or update the notes on a calendar event.
```

Event creation is not a user command — events come in from Google Calendar via the daemon sync.

### Agent audit and dev

```
jobcrm runs list [--agent <name>] [--status <s>] [--limit N]
jobcrm runs show <runId>                      Print full turn-by-turn transcript.
jobcrm runs replay <runId>                    Replay a run (against RecordedLlmClient) for debugging.

jobcrm dev record-agent <agentName> <scenarioName>
    Record a real LLM interaction to a fixture file. Interactive: prompts for the
    input, calls the real LLM once, writes the fixture. Intended for developers only.
```

### Maintenance

```
jobcrm db vacuum                              SQLite VACUUM; safe to run occasionally.
jobcrm db backup --to <path>                  Copy the DB file with the daemon paused.
jobcrm prune agent-runs --older-than 90d      Delete agent_run rows past retention.
```

---

## Output Conventions

### Tables

Default output for `list` commands. Column widths adapt to terminal width. Example:

```
$ jobcrm opp list --stage PANEL
REF        COMPANY   ROLE                       STAGE   UPDATED         OPEN TASKS
9f2e3a12   Stripe    Sr. Backend Engineer       PANEL   2 days ago      2
4b8c119d   Datadog   Staff SRE                  PANEL   5 days ago      0
```

### Detail views

```
$ jobcrm opp show 9f2e3a12
Opportunity  9f2e3a12
Company      Stripe
Role         Sr. Backend Engineer
Stage        PANEL   (advanced 2 days ago from HM_SCREEN)
Resume       resume-v3.pdf

Contacts (3)
  a8b9c012   Jane Recruiter        RECRUITER_INTERNAL
  ...

Recent interactions (5)
  2026-09-20  IN   email    Panel scheduling for Tue                Jane Recruiter
  2026-09-18  OUT  email    Re: HM screen thoughts                  Marc HM
  ...

Open tasks (2)
  t7f2...    THANK_YOU   due in 22h   Panel thank-you to Marc, Priya, Sam
  t9c1...    RESPOND     due in 3d    Reply with availability for onsite
```

### JSON mode

`--json` returns a stable schema. Example:

```
$ jobcrm opp list --stage PANEL --json
{
  "opportunities": [
    {
      "id": "9f2e3a12-...", "shortRef": "9f2e3a12",
      "companyId": "...", "companyName": "Stripe",
      "role": "Sr. Backend Engineer",
      "stage": "PANEL",
      "updatedAt": "2026-09-21T14:32:00Z",
      "openTaskCount": 2
    },
    ...
  ]
}
```

### Progress and long-running work

Any command that dispatches an agent prints a one-liner:

```
$ jobcrm draft t7f2a091
Dispatched DraftReplyAgent for task t7f2a091.
Run: r03b2c7a1  (status: RUNNING)
Waiting for completion...
Run finished: SUCCESS  (turns: 4, tokens: 3,140)
Draft: d19a...
Preview:
  Hi Marc, Priya, Sam,
  ...
Use `jobcrm drafts show d19a` for full text or `jobcrm drafts edit d19a` to modify.
```

Add `--async` to return immediately after dispatch and let the user check `jobcrm runs show <runId>` when convenient.

---

## Ingestion Flow Summary

| Source | Trigger | Path |
|--------|---------|------|
| Gmail email | Daemon poller every 5m, or one-shot `jobcrm sync-gmail`. | `GmailAdapter → RawEmailRepository → EmailIngestAppService → EmailIngestAgent`. |
| Google Calendar event | Daemon sync every 5m. | `CalendarAdapter → EventRepository → RulesEngine` (post-interview follow-up). |
| Phone-call notes | `jobcrm log call <opp>`. | Editor-prompted body → `CallNoteIngestAgent`. |
| LinkedIn thread | `jobcrm log linkedin`. | Editor-prompted body → `LinkedInIngestAgent`. |

All ingest agents write via `attachInteraction` (idempotent by `externalId` for email/LI, by `(opportunityId, occurredAt, first 64 chars of body)` for pasted call notes).

---

## Draft Review Flow

```
1. Rules engine or CLI dispatches DraftReplyAgent.
2. Agent produces a DraftMessage with status PENDING_REVIEW.
3. User runs:
     jobcrm drafts list
     jobcrm drafts show <ref>
     jobcrm drafts edit <ref>       # optional; opens $EDITOR
     jobcrm drafts approve <ref>    # status → APPROVED
4. User copies the approved body into Gmail and sends.
5. User runs:
     jobcrm drafts mark-sent <ref>  # status → SENT
```

The `mark-sent` step is manual in v1 because we don't send from the app. A future release may add a `send` tool wrapped in confirmation.

---

## Configuration File

`~/.jobcrm/config.yml` layout:

```yaml
data-dir: ~/.jobcrm
db:
  path: ~/.jobcrm/jobcrm.db

llm:
  provider: anthropic
  api-key: sk-ant-...          # or set via JOBCRM_LLM_API_KEY
  default-model: claude-sonnet-4-5

gmail:
  client-id: ...               # or set via JOBCRM_GMAIL_CLIENT_ID
  client-secret: ...           # or set via JOBCRM_GMAIL_CLIENT_SECRET
  refresh-token: ...           # written by `jobcrm auth gmail`
  poll-interval: 5m

calendar:
  poll-interval: 5m

daemon:
  briefing-time: "07:00"       # local
  stale-sweep-time: "03:00"    # local
  silence-threshold: 14d

agents:
  # per-agent overrides — see agent-architecture.md
  email-ingest: {...}
  draft-reply:  {...}
```

`jobcrm config set gmail.poll-interval 10m` edits this file safely.

---

## Non-Goals for v1

- No web UI or desktop UI.
- No mobile client.
- No autonomous sending of email, LinkedIn messages, or SMS.
- No push notifications; users must run `jobcrm today` or check drafts explicitly.
- No LinkedIn scraping or browser extension. LinkedIn ingest is manual paste only.
- No multi-user support. Single machine, single user, no auth layer.
