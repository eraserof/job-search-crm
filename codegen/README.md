# codegen — local plan/code pipeline (27b plans, 7b fills method bodies)

Developer tooling for this repo, not part of the app. The local 7b writes single **method bodies**;
a bigger model plans the work, writes the contracts, and repairs prompts that fail. The tool does
all the file work: it splices the body into the method, compiles, retries, escalates, restores the
file on failure, and commits each method that compiles. Every model call is one fresh message, so
each attempt and each stage starts from a clean context.

## Why body-only (not whole files)

The `llm-eval` study (local-only, not in the repo) showed the 7b is reliable at filling a
tightly-scoped method and unreliable at imports and whole-file edits. So the model writes only the
body, the tool adds imports, and anything you cannot specify exactly stays with a person or a
frontier model. What makes the 7b reliable is the contract: exact steps, exact strings, exact
calls. Batches whose contracts were precise came back right on the first try.

## One-time setup

```
ollama create coder-full-gpu-7b -f codegen\Modelfile
```

That's the pinned config from the study: qwen2.5-coder:7b, num_ctx 8192, temperature 0.2, all
layers requested on the GPU. The name must match `stages.code.model` in `models.json`.

`models.json` names the **platforms** (servers, OpenAI-compatible: Ollama, LM Studio, llama.cpp)
and the **stages** that use them (`plan`, `code`, `repair`). To use a model on another PC, set its
platform's `baseUrl` and make that server listen on the LAN (trusted network only). For LM Studio,
set the 27b's context length to about 49k and Max Concurrent Predictions to 1.

## Running it: `cg.cmd`

Run from the repo root:

```
.\cg models                                   list each platform's models (or why it's unreachable)
.\cg stats                                    per-stage summary of every recorded model call
.\cg scaffold codegen\requests\x.json         scaffold stage: request -> reviewable new types (see below)
.\cg plan codegen\requests\x.json             plan stage: request -> per-method jobs
.\cg run <plan folder> [--from N] [--no-auto-commit]
.\cg job <job.json> [--code-attempts N] [--repair-rounds N] [--no-compile] [--no-auto-commit]
```

`cg.cmd` rebuilds the tool first (`gradlew :codegen:installDist`, a few seconds when nothing
changed), then runs the generated launcher `codegen\build\install\codegen\bin\codegen.bat`. The
rebuild matters because the pipeline edits codegen's own sources, and the installed launcher is a
snapshot. Type `.\cg`, not `cg`: this machine sets NoDefaultCurrentDirectoryInExePath, so cmd does
not run programs from the current folder by bare name.

Exit codes: `0` compiled, `1` failed (target restored), `2` refused or bad input, `3` the model
asked a question even after repair, `4` model unavailable, `5` compiled but the commit failed
(change left in place, uncommitted).

## The loop

1. **Stubs.** The tool only fills methods that already exist. Write the signatures (or have the 27b
   scaffold them, below) with the body `throw new UnsupportedOperationException("TODO(codegen)");`
   and the contract as Javadoc. Commit them.
2. **Request.** A file in `codegen\requests\`: `{ "ask": "...", "context": ["path", ...] }`. The
   context files are inlined whole, so list the target plus the types its methods use. Name the
   exact methods to fill; ask the planner to copy the contract's steps and strings exactly.
3. **Plan:** `.\cg plan <request>`. The 27b returns one JSON job per method; each is checked against
   the real file (target exists, signature found exactly once) before anything is written. A
   rejected plan is re-asked once with the errors. On LM Studio the JSON shape is enforced with
   `response_format`.
4. **Review** `codegen\drafts\plans\<name>-<ts>\NN-*.prompt.txt` and edit if needed. This is the
   cheapest place to fix a mistake.
5. **Code:** `.\cg run <that folder>`. Jobs run in order and stop at the first failure; resume with
   `--from NN`.
6. **Format, test, review:** `gradlew :codegen:spotlessApply`, `gradlew test`, read the commits.

A job file (`NN-<id>.job.json`, or your own under `codegen\jobs\`) is:

```json
{ "stage": "code", "target": "src/main/java/.../Foo.java",
  "signature": "public ToolResult execute(String argsJson, ToolContext ctx)",
  "prompt": "codegen/drafts/foo.txt", "imports": ["java.util.UUID"] }
```

## Escalation (`escalation` in `models.json`)

Each job goes up a ladder:

1. `code` stage, `codeAttempts` times. Retries get the compiler errors as a warning, but never the
   failed code, because the 7b tends to copy back whatever it is shown.
2. `repair` stage (the 27b) rewrites the job prompt to fix the root cause. It does not write code.
3. `code` stage again with the revised prompt, for `repairRounds` rounds.
4. If it still fails, the target is restored and the job stops. The target is also restored if the
   run crashes or is cancelled.

Before the slow Gradle compile, the body is syntax-checked with JavaParser. If a repaired prompt
succeeds, it replaces the job's prompt file and the first original is kept as `*.orig`. A
`NEEDS_CLARIFICATION` reply also goes to repair, so the 27b can answer it. The trail is in
`codegen\drafts\runs\<job>-<ts>\`: `r<round>-a<attempt>.*`, `repair-<round>.*`, and
`original.java.bak`. Override per run with `--code-attempts N --repair-rounds N`
(`--repair-rounds 0` turns escalation off).

## Auto-commit (default)

Each job that compiles becomes its own commit containing only its target file, with a subject like
`codegen: PlanPrompts.planner (3 attempt(s), repaired)` and a body naming the plan, the model, and
the run folder. A failed job leaves no commit. A job refuses to start if the branch is `main` or
`master`, or if the target has uncommitted changes (so each commit holds only the model's change).
To drop one job, `git revert <sha>`. `--no-auto-commit` turns it off; `--no-compile` does too, since
uncompiled code is never committed. Don't commit by hand while a run is active.

## Review (bug catcher, layer 1)

After a job compiles, the tool checks the new body against the contract in the method's own
Javadoc: every line in the contract that is a quoted string (the `<pre>` block the scaffold writes)
must appear in the body. Missing ones are printed and written to `review.txt` in the run folder,
and the commit still happens. It makes no model call. HTML entities in the Javadoc (`&lt;`, `&gt;`,
`&amp;`, `&#64;`) are decoded first. It only proves the text is present, not that it is used
correctly, and it skips `--no-compile` runs. A method with no Javadoc, or no quoted lines, checks
nothing and says so.

## Model calls

- **Streaming with two timeouts.** `firstTokenTimeoutSec` covers queueing and model loading;
  `idleTimeoutSec` covers a stall once tokens flow. Reasoning tokens count as activity.
- **Retry with backoff.** 30, 60, then 120 seconds on busy, unreachable, or stalled models; bad
  requests fail right away.
- **Reasoning.** Only a reply that starts with `<think>` is stripped, and marker parsing takes the
  last block: reasoning that leaks into `content` often quotes the markers, and `</think>` can be
  data. If `content` is empty, `reasoning_content` is used instead.
- **One run per checkout.** A run holds `codegen/drafts/.lock`; a crashed run's lock is taken over.

## Which JDK runs what

You don't need to set `JAVA_HOME` for this repo. Gradle runs its daemon on a pinned JDK 25
(`gradle/gradle-daemon-jvm.properties`), and `gradle.properties` turns off JDK auto-detection, so
the daemon and the compile toolchain use only JDKs Gradle provisioned under `~/.gradle/jdks`.
`cg.cmd` runs the tool on that same JDK (recorded in `codegen\build\install\codegen\java-home.txt`).
This matters because google-java-format (Spotless) works against the compiler internals of whatever
JDK runs Gradle: it failed with `NoSuchFieldError ... endPositions` on JDK 21, on JDK 27, and on a
half-updated IDE-bundled JDK 25. After changing these files, run `gradlew --stop` once.

## Writing contracts (lessons from the batches)

- Give exact calls with full signatures, not descriptions: `Files.readAllLines(file, UTF_8)`.
- Put every string, regex, and format literal on one line. Never let a quoted string wrap across
  Javadoc lines: the 7b copies the line break into the string.
- Give the exact exception type and message for every edge case.
- Pass lambdas inline; qualify nested types (`Outer.Inner`).
- Compiling is not correctness: of 21 methods filled from early contracts, 4 compiled and were
  wrong. Write tests for each batch.

## Scaffold stage (experimental)

`src/main/resources/com/jobcrm/codegen/scaffold-prompt.txt` has the 27b write a skeleton: new types,
signatures, contracts, and `TODO(codegen)` stubs, with anything it may not do listed under MANUAL.
The metrics package (`com.jobcrm.codegen.metrics`) was built this way and then filled by the 7b,
10 of 10 methods on the first try.

```
.\cg scaffold codegen\requests\x.json [--retries N]   request -> codegen\drafts\scaffolds\<name>-<ts>\
.\cg scaffold-apply <draft folder>                    copy the reviewed files\ tree into the repo
```

`scaffold` never touches the source tree. It parses the reply into blocks, validates them (path
inside the repo, `.java`, no overwrite of an existing file, package matches directory, no wildcard
imports, stub message exactly `TODO(codegen)`, closing markers present) and re-asks once with the
problems if that fails. The draft folder holds `files/` (new files at their repo paths),
`add-to-N.txt`, `MANUAL.txt`, `prompt.txt` and `reply.txt`. Review it, then run `scaffold-apply`,
which writes all files or none (it refuses if any target exists). Then compile with the stubs in
place before running the code stage.

Lessons from three live runs: one or two types per request; a budget of about 40k tokens
(reasoning alone used 21k-26k); a `finish_reason: length` or empty reply is a budget failure.

## Known gaps

- **Scaffold `ADD TO` blocks are not applied (TODO, look into).** Members for an existing class are
  validated and saved as `add-to-N.txt` in the draft folder, but merging imports and inserting
  members before the class's closing brace is left to you.
- **Scaffold to `run` hand-off.** Scaffold leaves Javadoc-contract stubs; `run` consumes
  `*.job.json`. Nothing turns stubs into jobs yet, so write a request that names the stubs and use
  `plan`.
- **No scaffold token cap.** `StageSpec` has no max-tokens field, so a runaway reasoning budget is
  only stopped by the idle and first-token timeouts.

- **No auto-import yet.** The retired PowerShell orchestrator resolved `cannot find symbol: class
  X` without a model call (repo imports first, then repo classes, then common JDK names). The Java
  tool does not, so a missing import goes up the escalation ladder. List needed imports in the
  request until this is ported.
- **`stats` has no per-job view yet.** Calls are recorded with a job label (`Class.method` for code
  and repair, the request name for plan and scaffold), but the table only groups by stage. Servers
  that send no `usage` chunk record 0 tokens.

## Manual one-off: `gen.cmd`

For a single method without the pipeline: copy `TEMPLATE.txt`, fill it in, run
`codegen\gen.cmd codegen\drafts\myMethod.txt`, review, and paste the body yourself. It prints the
body fence-free and never touches your files. Its house rules (record accessors with `()`, mutable
accumulator lists, acquire-before-try, Callable vs Runnable) are also what every pipeline prompt
starts with.
