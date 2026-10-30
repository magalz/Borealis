# Orchestration

The main Claude session orchestrates Borealis work. It delegates exploration, light text work, implementation and review to subagents defined in `.claude/agents/`, and leaves mechanical decisions to hooks, scripts and CI. Models are set in the agent files and in `.claude/settings.json`. This organization is adapted from the SuiteWard project and recorded in the ADRs (see the [ADR index](../adr/README.md)).

| Role | Model | Responsibility |
| --- | --- | --- |
| Orchestrator (main session) | Opus, high effort | Plans phases, writes briefs, creates worktrees, dispatches subagents, integrates, verifies, opens the phase PR, reports. |
| `br-scout` | Haiku, medium effort | Read-only discovery: Memtrace first, then targeted reads; structured summaries. |
| `br-scribe` | Haiku, medium effort | PR descriptions, release notes, changelog entries, issue text, ADRs from orchestrator notes, documentation copy-edits. Never code, never publishes. |
| `br-implementer` | Sonnet, extra high effort (`xhigh`) | One task per worktree, test-first. |
| `br-reviewer` | Sonnet, extra high effort (`xhigh`) | Independent review of each task and of the phase diff. |
| Hooks, CI and scripts | none | Formatting, lint, tests, builds, supply-chain checks. |

`CLAUDE_CODE_SUBAGENT_MODEL=haiku` also applies to built-in agents that inherit the model (such as `general-purpose` and `Plan`). Pass `model: "sonnet"` (or `"opus"`) explicitly when using them for design or substantive work.

Memtrace holds the graph of the orchestrator's checkout, which stays on the phase branch while a phase runs (integration happens there; the phase branch is not checked out in another worktree). Memtrace (1.2.10 and later) indexes a checkout only under the branch it has checked out and refuses any other label, so the orchestrator indexes it incrementally with the phase branch (for example `branch: "phase/s11-appearance"`) and re-runs that index after each merge wave; after the owner merges the phase, the checkout is on `main` and is indexed as `main`. The daemon runs with `MEMTRACE_NO_STARTUP_WATCH=1` and `MEMTRACE_NO_WATCH_RESTORE=1`, so `memtrace start` arms no watcher, and the orchestrator does not call `watch_directory`: answers reflect the last index of the checkout, not live edits. Read the file in your own worktree before editing it. Every Memtrace call passes the indexed branch when the tool takes a branch (`get_symbol_context`, `analyze_relationships`, `get_impact`, `detect_changes` and the quality tools). The store keeps older copies of the repository under other labels (older phases, task and docs branches), so a call without the branch mixes them and comes back ambiguous, empty or slow. The quality tools follow the selected branch and its recorded ancestry. `find_symbol`, `find_code` and `preflight_check` take no branch: scope them with `repo_id` and, when needed, `file_path`. When two symbols share a name, pass `file_path` or the `node_id` from an earlier result; `preflight_check` by name can answer `ambiguous` with one candidate per label. `preflight_check` and `get_impact` have no size limit and return over 200,000 characters on a widely used symbol (200 rows, mostly tests); prefer `get_symbol_context` with `limit` there. One index job runs at a time per repository. An index sent while another runs answers `busy` with `retry_after_s`, and a job stays `running` for several minutes after its record is visible. Heavy reads during an index (`get_impact`, `preflight_check`, `find_dead_code`) can make it fail with `os error 33`, so the orchestrator indexes when no agent is querying. A task's own changes are an overlay with its task branch: the orchestrator indexes the task worktree and subagents pass `worktree` to `find_code` (see `AGENTS.md`).

The graph leaves some files out. The tracked `.memtraceignore` keeps Gradle Kotlin DSL scripts (`**/*.gradle.kts`), the design tokens and the prebuilt OpenSSL and Opus headers out of it, and Memtrace's default exclusions drop `**/third_party/**`, so `core/third_party/` is not in the graph: use Grep and ranged reads there. Only `br-scout`, `br-implementer` and `br-reviewer` have Memtrace tools; `br-scribe` has none.

The impact tools (`get_impact`, `preflight_check`, `detect_changes`) are the main use of Memtrace. Its dead-code and duplicate-code results are leads, not evidence: a deletion needs a manual check for callable references (`::f`), Compose, JNI, the manifest, reflection and Hilt.

Entire Brain (the `entire brain` commands of `.entire/agent-guide.md`) has a separate role, set by the owner on 2026-10-27. It answers why code is the way it is and what was decided or tried before (`entire brain recall`). For code questions it is the fallback: when Memtrace is unavailable or answers wrong and Brain can answer (`entire brain inspect …`), agents ask Brain before targeted Grep and ranged reads. Its answers are leads like Memtrace's, and its index can trail the checkout, so check `entire brain status` when an answer looks stale. Brain runs in hosted mode, so its queries leave the machine; never put secrets in them.

## Roles

- **Orchestrator**: plans phases, writes one [brief](task-brief.md) per task, creates worktrees, dispatches subagents, integrates reviewed work, runs phase checks, pushes the task and phase branches, opens the phase PR under the owner's account, and reports to the owner. It owns shared files (Gradle settings and version catalog, CI workflows, `AGENTS.md`) and decides conflicts. It sends discovery to `br-scout` instead of reading broadly itself.
- **`br-scout`**: answers "where, what calls, what breaks" questions with references and short summaries; never edits.
- **`br-scribe`**: writes text from the orchestrator's notes and repository facts; edits only the text files its brief names.
- **`br-implementer`**: implements exactly one brief inside its worktree, test-first per the [TDD rule](tdd.md), and returns a report. It never pushes, opens PRs, or touches files it does not own; if the brief is wrong it stops and reports.
- **`br-reviewer`**: given the same brief, independently checks the branch for correctness, scope, inherited-code rules, and test-before-implementation commit order. It reports findings but does not fix them.

## Task lifecycle

1. Brief: goal, owned files, consumed contracts, acceptance, verification.
2. Worktree at `D:\Repos\Borealis-worktrees\<phase>-<task>` and branch `task/<phase>-<task>` created from the phase branch `phase/s<n>-<name>` (for example `task/s8-docs-guides` from `phase/s8-tidy-up`).
3. The orchestrator indexes the worktree as a Memtrace overlay with the task branch when the task needs code discovery; graph and quality queries stay on the indexed phase branch.
4. `br-implementer` works test-first and runs the checks from the brief.
5. `br-reviewer` reviews. Blocking findings go back to the implementer for at most two fix rounds; then the orchestrator decides, or escalates to the owner.
6. The orchestrator merges the task branch into the phase branch with `--no-ff`.
7. When the phase's tasks are merged, the orchestrator runs the phase checks on the final revision.
8. `br-scribe` drafts the PR description; the orchestrator pushes the task and phase branches and opens one PR for the phase with the owner's GitHub account. Subagents never push or open PRs.
9. The owner reviews and merges it with a merge commit, so test-first commits are preserved. Agents never merge.
10. After the owner merges, the orchestrator cleans up:
    - stops the Gradle daemons (`./gradlew --stop` from `apps/android`, once no build runs), because a running daemon keeps files of a worktree's `build` folders open and `git worktree remove` then fails;
    - `git worktree remove` for each task worktree, then `git worktree prune`;
    - deletes the task branches, local and remote;
    - runs the Memtrace `cleanup_worktrees` tool for `repo_id: Borealis`, which drops the overlays of the removed worktrees;
    - checks out `main` in the orchestrator's checkout and pulls the merge;
    - wipes the whole Borealis store, running each command in `D:\Repos\Borealis`: `memtrace stop`, then `memtrace reset` with no repository, which empties `D:\Repos\Borealis\.memdb` and moves its history to `.memdb.history`, then `memtrace history purge --store D:\Repos\Borealis\.memdb --delete-forever`, then starts the daemon again (`memtrace start --headless --no-workspace` in `D:\Repos\Borealis`, with `MEMTRACE_NO_STARTUP_WATCH=1`, `MEMTRACE_NO_WATCH_RESTORE=1` and `MEMTRACE_NO_REPLAY=1`, the last one set only in the environment that launches the daemon). This drops every record, branch label and history replay. The per-repository `memtrace reset Borealis` is not used: on a store of 28 GB its delete gave no answer within two hours (2026-10-23);
    - lets the daemon index the checkout again from scratch: on an empty store it starts that index on its own, as `main`, and an `index_directory` call meanwhile answers `peer_indexing`. It follows the job with `list_jobs` and `check_job_status`, then checks with `list_indexed_repositories` that `graph_head_sha` is the merge commit. Without `MEMTRACE_NO_REPLAY`, the daemon's first index replays the git history before it computes the embeddings, and the replay holds the store lease: `find_symbol` and `find_code` fail, no embeddings exist until it ends, and a replay cut short leaves duplicate symbols. `defer_replay: true` on `index_directory` does not prevent this, because the daemon's own index starts first (2026-10-26). The history tools lack the old commits until `replay_history` runs.

## Parallel waves

Tasks without shared files and with satisfied dependencies run in parallel in one wave. Shared declarations (Gradle settings, version catalog, CI, the wiring in inherited files) have exactly one owner per wave; a task needing such a file it does not own stops and reports. A later wave starts only after its dependencies are merged into the phase branch, or when it depends only on a frozen contract document.

## Lessons from Sprint 10

- **Scratch files.** Each agent works in a scratch folder of its own, named after its task, and never deletes, moves or overwrites anything outside it: not another task's folder, not the briefs or the notes, and nothing at the root of the scratch area. The session scratch area lives under `%TEMP%`, and the owner's monthly cleanup wipes `%TEMP%`; it was also wiped once during a run (2026-10-20), so nothing there is a record. Keep results in the repository or in the report. Never run a recursive delete on a folder that holds links or junctions, or on a path built from a variable that could be empty.
- **Mutation tests.** To test a mutation of the code, an agent uses `git worktree add --detach` under `D:\Repos\Borealis-worktrees\zz-<name>`, and removes it with `git worktree remove --force`.
- **Remove merged worktrees promptly.** Each worktree holds a full checkout and its Gradle build output, which is large. After a task is merged, the orchestrator runs `git worktree remove` for it and `git worktree prune` at once, and does not wait for the end of the phase, so the disk does not fill.
- **Gradle file locks can leave a worktree folder behind.** On Windows a Gradle daemon or an IDE can hold a file open, so `git worktree remove --force` may fail or leave the folder. An agent does not delete such a folder: it reports it, and the orchestrator deletes it once the lock is gone.
- **Several builds at once.** Tasks build on the same machine in parallel. If a daemon dies for lack of memory, wait and run the command again; never kill processes. On the KSP error "different roots", run the task again with `--rerun`.

## Lessons from Sprint 13

- **Mutation runs and disk space.** On 2026-10-25 mutation loops (one test JVM per mutant) left 11,469 Robolectric folders and 4,982 Conscrypt libraries in `%TEMP%` and filled the C: drive. Rules since: at most 60 mutants per task, one at a time, in the foreground (never in the background, so a loop ends with its agent); no `--fail-fast` in a mutation run; never mutate a time, a delay, a loop bound or a sleep to a large value (one mutant made a test sleep for 24 days, and its processes had to be stopped by hand); count the `robolectric-*` folders before and after a run; after stopping an agent, the orchestrator checks for and stops the processes it started.
- **Deleting folders.** When asking the owner to delete leftover folders, name each folder exactly and say whether any agent is still running. A cleanup by prefix deleted an active review worktree.
- **Windows path length.** A test temporary folder deep under the checkout made Robolectric's SQLite paths pass 260 characters. The build now puts the folder at the drive root on Windows. After changing test temp paths, run the tests that open SQLite too.
- **Characterize before converting a large class.** `ControllerHandler` (3,659 lines) was pinned by three characterization tasks working in parallel on separate test packages, then converted in one task with every test unchanged, and reviewed in two halves.
- **Worktree removal on Windows.** A worktree whose `build/` holds paths over 260 characters cannot be removed by `git worktree remove` ("Invalid argument"): git unregisters it and the folder stays. The orchestrator removes such a folder with a `\\?\` path, as it did in this sprint. The phase review confirmed the cause: 2,171 paths in that worktree's `build/` passed 260 characters.

## Lessons from Sprint 15

- **A failing commit fails on behaviour.** The `test:` commit of a behaviour change carries the smallest unwired stub (a constant, an empty function, a parameter nobody reads), so the new tests compile and fail on their assertions. A test that cannot compile is not an observed failure (the first failing commit of #102 was one).
- **One Gradle run at a time, also across tasks.** Two tasks that build in parallel share one lock folder in the session scratch area: `until mkdir "$L" 2>/dev/null; do sleep 30; done; ./gradlew ...; rc=$?; rmdir "$L"`, released even when the run fails, and never removed by an agent that did not take it. With about 8 GB free, two builds at once ran out of memory.
- **Gate the phase after merging parallel tasks.** Two tasks that each pass their gates can still break each other: in the UAT wave one task removed a type that the other task's new tests used. Run both gates on the phase head after the last merge of a wave, and fix such a break on the phase with its own commit.
- **Git Bash rewrites arguments that start with `/`.** `gh issue create --title "/cancel …"` became `C:/Program Files/Git/cancel …`, and `adb shell … /sdcard/…` paths broke the same way. Set `MSYS_NO_PATHCONV=1` for those commands, and pass local files to Windows programs with `cygpath -w`.
- **Read a device screen only when it is safe.** Before a `uiautomator dump`, check that the device is unlocked and that Borealis is in front: a dump of the S22 lock screen read the owner's notifications (the file was deleted at once). Never dump the S22 while a landscape activity is up. To drop a stream without losing adb, block only the app's network (`cmd connectivity set-chain3-enabled true` and `set-package-networking-enabled false <package>`; runbook 3.29, step 7) and restore it right after.
- **Wireless adb on the S22.** It stops advertising when the phone sleeps. Find it again with `adb mdns services` and `adb connect`; when it is gone, the owner turns wireless debugging back on or plugs in USB.
- **Filing in LuminalShine.** Blank issues are off there: a bug follows the fields of the bug form (symptoms, steps, expected, Windows version, logs), and a feature request goes to Discussions (Ideas). Links to host source are pinned to a release commit, and nothing is filed without the owner's OK.

## Reporting to the owner

The orchestrator reports, per phase and honestly: behaviour changed, commits and their test-first order, checks run and the exact revision tested, remaining limitations and open decisions, and any blocked publication. It asks before any push to `main`, any repository setting change and any message to other people.
