# codegen roadmap

Running list of open work and ideas for the codegen tool. Newest thinking goes at the top of each
section. Move an item to "Done" with a date when it lands.

## Open items

- **Port auto-import.** The retired PowerShell orchestrator resolved `cannot find symbol: class X`
  without a model call (repo imports first, then repo classes, then common JDK names). The Java tool
  makes a missing import climb the escalation ladder.
- **Metrics: record the job name.** Calls are recorded per stage (see Done), but `CallRecord.job` is
  always null. Needs a way to tell the client which job it is working for, e.g. a field on the
  recorder set by `JobRunner`.
- **Metrics: nicer `stats` output.** The table is space-separated, not aligned. Maybe add `--since`
  or `--stage` filters.
- **Metrics: show reasoning and time to first token in `stats`.** `reasoningTokens` and
  `timeToFirstTokenMillis` are recorded but not reported, so we cannot tell how much of a 14k-21k
  token plan or scaffold call was thinking.
- **Metrics: redefine `ctps`.** It is completion tokens divided by total call time, which includes
  model load and prompt processing, so it reads 4 tok/s for the 7B. Dividing by (total minus time to
  first token) would give generation speed.
- **Format before commit in `run`.** `run` only compiles, so the 7B's 4-space indentation was
  committed and `spotlessCheck` failed afterward. Run `spotlessApply` on the target before
  committing.
- **Plan largely copies the scaffold's contracts.** For the ContractCheck run the plan prompts were
  the Javadoc steps re-typed, at a cost of ~4 minutes and 14.6k tokens of the 27b. When the stubs
  came from a scaffold, jobs could be derived from the Javadoc without a model call. Same item as the
  scaffold to `run` hand-off above.
- **Bug catcher, layer 1 (`ContractCheck`) is built but not wired in.** `ContractCheck.missing` is
  done and tested. Still to do: get the Javadoc above a method (`MethodLocator` returns only body
  ranges), call it from `JobRunner` after a successful compile, and write warnings to `review.txt` in
  the run folder. Warn only, never block the commit.
- **Task 15's nine remaining tools** (main app; the first four read tools are on
  `feature/task-15-read-tools`).
- **VectorGraph**, when we get to it.

## Scaffold stage follow-ups

- **`ADD TO` blocks are not applied.** They are validated and saved as `add-to-N.txt`; merging
  imports and inserting members before the class's closing brace is manual. Look into it.
- **Scaffold to `run` hand-off.** Scaffold leaves Javadoc-contract stubs; `run` consumes
  `*.job.json`. Something has to turn each stub into a job (target, signature, prompt).
- **No max-tokens cap per stage.** `StageSpec` has no such field, so runaway reasoning is stopped only
  by the first-token and idle timeouts.
- **Truncated replies.** The first real run was cut off mid-file. Context length in LM Studio, smaller
  asks, or fewer context files are the levers. A leaner retry prompt was considered and judged not
  worth it for now.
- **Validator and applier tests.** Parser and validator have tests; `ScaffoldApplier` and `Scaffolder`
  do not.

## Ideas

### Spec Refiner (new stage, before scaffold/plan)

Takes a vague request ("add caching") and breaks it into smaller pieces until the next stage can work
on one.

- A few micro steps that loop. Each pass shrinks the problem and the context needed to solve it, so
  any amount of vagueness can be reduced a little at a time.
- Keep the work in a graph of some kind. It becomes the repository for the bigger ask, and agents can
  read each other's work instead of re-deriving it.
- Loop ends when a piece is small enough for scaffold or plan to take as a single request.
- Open questions: what a node is (question, decision, sub-task?), how to decide "small enough", and
  whether the graph lives as files in `drafts/` or in a store.

### Bug catcher (new stage, after code)

Runs after the coder to catch simple bugs the small model misses: missing null checks, exceptions that
should be thrown or caught, off-by-one on empty input. Cheap checks first (static or pattern based),
a model pass only where needed.

### Knowledge graph for docs

KiroGraph indexes code. Add a graph for specs, design docs and the bigger picture, so agents can look
up why something exists, not just what it is.

### Richer context from KiroGraph

Stages currently inline whole files into prompts (`PlanPrompts.contextBlock`). Use KiroGraph to supply
only the relevant symbols, signatures and callers instead. This shrinks prompts and may reduce
truncation.

## Branch notes

Two branches share the first codegen commit (`c5e533f`) and can be merged in either order:

- `feature/codegen-java`: the tool and everything since.
- `feature/task-15-read-tools`: the first four tools and the persistence fixes.

## Done

- 2026-11-09: first full scaffold, plan, code run on a new feature (`review.ContractCheck`). Both
  bodies compiled on the first attempt with no repair. Cost: scaffold ~6 min and 21k completion
  tokens, plan ~4 min and 14.6k, code ~34 s per method.
- 2026-11-08: metrics wired in. `OpenAiChatClient` records every attempt (tokens, time to first
  token, total time, success) to `codegen/drafts/metrics/calls.jsonl`; `.\cg stats` prints the
  per-stage table.
- 2026-11-08: `scaffold` and `scaffold-apply` commands (draft folder, validation, one retry, fails
  fast when the ask collides with existing files).
