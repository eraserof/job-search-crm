# codegen — one-method body generator (local 7B)

The daily loop that came out of the `llm-eval` study. The local model writes a single **method
body**; you place it, let the IDE add imports, compile, review, and commit. The model never edits
your files. One method at a time, not batches — batching only paid off on a rented, metered GPU.

## Why body-only (not whole files)

The study showed the 7B is reliable at filling a tightly-scoped method and unreliable at imports and
whole-file edits (every `PASS*` in the eval was the model getting imports wrong and the harness
re-adding them). So imports are the IDE's job, and file editing — if ever wanted — belongs on the
$20 frontier tier, not the local 7B. (The `llm-eval` study itself is local-only and not in the repo.)

## One-time setup

```
ollama create coder-full-gpu-7b -f codegen\Modelfile
```

(That's the pinned config from the study: qwen2.5-coder:7b, num_ctx 8192, temperature 0.2, all
layers requested on the GPU. The name must match `stages.code.model` in `models.json`.)

## The loop

1. **Decompose** the feature yourself: write the method signatures and the wiring. This is the
   judgment work and stays with you.
2. **Write a prompt** for one method by copying `TEMPLATE.txt`. Fill in the class fields, the exact
   signatures of every type the method uses (with record accessors), the method signature, and the
   behavior. Put drafts in `codegen\drafts\` (gitignored).
3. **Generate:** `codegen\gen.cmd codegen\drafts\myMethod.txt`
4. **Review every line.** If it is wrong, fix by hand or add a clarifying line to the prompt and
   regenerate. If it printed `NEEDS_CLARIFICATION: ...`, answer it in the prompt and rerun.
5. **Paste** the body into the method. Run "organize imports" in your IDE.
6. **Compile and test:** `gradlew.bat test` (or the relevant module).
7. **Commit** that one method. Repeat.

## The house rules

`TEMPLATE.txt` bakes in the fixes the study surfaced across every model — record accessors with
`()`, mutable accumulator lists, acquire-before-try, Callable-vs-Runnable. Keep them; they prevented
most of the compile/behaviour failures for free.

## Notes

- `*.out.java` raw outputs and the `drafts\` folder are gitignored (see `.gitignore`).
- Pass a different model as the second arg if you want to compare, e.g. `gen.cmd prompt.txt llama3`.
- This is the "cheap tier." Keep architecture, debugging, and anything you cannot fully specify on
  the frontier tier.

## Automated loop: `orchestrate.ps1`

Same contract (model writes only the body), but the script does the file work:

1. Put a stub (or existing) method in the target file with its final signature.
2. Write the prompt as before, then a small job file in `codegen\jobs\`:
   ```json
   { "stage": "code", "target": "src/main/java/.../Foo.java",
     "signature": "public ToolResult execute(String argsJson, ToolContext ctx)",
     "prompt": "codegen/drafts/foo.txt", "imports": ["java.util.UUID"] }
   ```
3. Run: `powershell -NoProfile -ExecutionPolicy Bypass -File codegen\orchestrate.ps1 codegen\jobs\foo.json`

It calls the stage's endpoint from `codegen\models.json`, which works with any OpenAI-compatible
server (Ollama, llama.cpp, LM Studio). It then splices the body into the method, runs
`gradlew compileJava`, and on failure retries with the compiler errors as a fresh prompt (default 2
retries). If every attempt fails, it restores the file. Prompts, outputs, compiler logs, and
`original.java.bak` are kept in `codegen\drafts\runs\<job>-<timestamp>\`. Flags: `-Retries N`, `-NoCompile`.

Every call is a single fresh message, so each attempt and stage starts from a clean context. To use
the 27b on another PC, set `stages.plan.baseUrl` and `model` in `models.json`, and have that
Ollama listen on the LAN (`OLLAMA_HOST=0.0.0.0`, firewall port 11434, trusted network only).

## Plan stage: `plan.ps1` + `run-plan.ps1`

For a larger ask, the 27b writes the job prompts and the 7b fills them in.

1. Write a request in `codegen\requests\`, e.g. `findByContact.json`:
   `{ "ask": "...", "context": ["path/to/File.java", ...] }`. The context files are inlined
   whole, so include the target file plus everything its methods touch.
2. Plan: `powershell -NoProfile -ExecutionPolicy Bypass -File codegen\plan.ps1 codegen\requests\x.json`.
   The 27b returns JSON jobs, one per existing method signature. Each job is checked against the
   real file before anything is written. On LM Studio the JSON shape is enforced with `response_format`.
3. Review `codegen\drafts\plans\<name>-<ts>\NN-*.prompt.txt` and edit them if needed.
4. Code: `powershell -NoProfile -ExecutionPolicy Bypass -File codegen\run-plan.ps1 <that folder>`.
   It runs the jobs in order and stops at the first failure. Resume with `-From NN`.

Limits: the planner only fills methods that already exist, so add stubs first. On this machine
`NoDefaultCurrentDirectoryInExePath=1`, so gradle is called by full path. In LM Studio, raise the
27b's context length if large requests get truncated.

## Escalation (`escalation` in `models.json`)

Each job goes up a ladder:

1. `code` stage, `codeAttempts` times. Retries get the compiler errors as a warning, but not the
   failed code, because the 7b tends to copy back whatever it is shown.
2. `repair` stage (the 27b) rewrites the job prompt to fix the root cause. It does not write code.
3. `code` stage again with the revised prompt. This repeats for `repairRounds` rounds.
4. If it still fails, the file is restored and the job stops. The file is also restored if the run
   crashes or is cancelled.

If a repaired prompt succeeds, it is saved over the job's prompt file and the original is kept as
`*.orig`. A `NEEDS_CLARIFICATION` reply also goes to repair, so the 27b can answer the question.
The trail is in the run folder: `r<round>-a<attempt>.*` and `repair-<round>.*`. Override per run
with `-CodeAttempts N -RepairRounds N` (`-RepairRounds 0` turns escalation off).

## Auto-commit (default)

Each job that compiles becomes its own commit, containing only its target file, with a message
like `codegen: findByContact (3 attempt(s), repaired)`. A failed job leaves no commit. Before
starting, a job refuses to run if:
- the current branch is `main` or `master`, or
- the target file has uncommitted changes, so a job's commit only ever holds the model's change.

Commit your stubs first, then run the plan. To drop one job's change, `git revert <sha>`. Turn it
off with `-NoAutoCommit` on `run-plan.ps1` or `orchestrate.ps1`. `-NoCompile` also turns it off,
since uncompiled code is never committed. Exit code 5 means the code compiled but the commit
failed, for example because of a hook. The change is left in place, uncommitted.

## Java port (in progress): `codegen` Gradle subproject

The PowerShell scripts are being replaced by a Java tool under `codegen/src/`. It reads the same
`models.json`, request, plan, and job files. Build and run it:

```
gradlew :codegen:installDist
codegen\build\install\codegen\bin\codegen.bat models
codegen\build\install\codegen\bin\codegen.bat plan codegen\requests\x.json
codegen\build\install\codegen\bin\codegen.bat run <plan folder> [--from N] [--no-auto-commit]
codegen\build\install\codegen\bin\codegen.bat job <job.json> [--code-attempts N] [--repair-rounds N] [--no-compile]
```

What it adds over the scripts:
- **Streaming with two timeouts.** `firstTokenTimeoutSec` covers queueing and model loading,
  and `idleTimeoutSec` covers a stall once tokens are flowing. Reasoning tokens count as
  activity. The scripts' `timeoutSec` is ignored.
- **Retry with backoff.** Waits 30, 60, then 120 seconds on busy, unreachable, or stalled
  models. Bad requests fail right away.
- **One run per checkout.** A run holds `codegen/drafts/.lock`. A crashed run's lock is taken
  over automatically.
- **JavaParser method lookup.** It matches by method name and parameter types instead of
  regex, and syntax-checks the body before the slow Gradle compile.
- Exit codes are the same as the scripts, plus `2` (refused or bad input) and `4` (model
  unavailable).

Status: the hand-written core is complete and tested. That covers method lookup and splicing,
the streaming client, the escalation loop, the planner wiring, and the CLI. Methods whose body
is `throw new UnsupportedOperationException("TODO(codegen)")` are stubs for the PowerShell
pipeline to fill, and each one's javadoc is its contract. When they are filled, enable the two
`@Disabled` tests in `JobRunnerTest`.
