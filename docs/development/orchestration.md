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

## Roles

- **Orchestrator**: plans phases, writes one [brief](task-brief.md) per task, creates worktrees, dispatches subagents, integrates reviewed work, runs phase checks, pushes the task and phase branches, opens the phase PR under the owner's account, and reports to the owner. It owns shared files (Gradle settings and version catalog, CI workflows, `AGENTS.md`) and decides conflicts. It sends discovery to `br-scout` instead of reading broadly itself.
- **`br-scout`**: answers "where, what calls, what breaks" questions with references and short summaries; never edits.
- **`br-scribe`**: writes text from the orchestrator's notes and repository facts; edits only the text files its brief names.
- **`br-implementer`**: implements exactly one brief inside its worktree, test-first per the [TDD rule](tdd.md), and returns a report. It never pushes, opens PRs, or touches files it does not own; if the brief is wrong it stops and reports.
- **`br-reviewer`**: given the same brief, independently checks the branch for correctness, scope, inherited-code rules, and test-before-implementation commit order. It reports findings but does not fix them.

## Task lifecycle

1. Brief: goal, owned files, consumed contracts, acceptance, verification.
2. Worktree at `D:\Repos\Borealis-worktrees\<phase>-<task>` and branch created from the phase branch `phase/s<n>-<name>`: `task/<phase>-<task>` (for example `task/s8-docs-guides` from `phase/s8-tidy-up`), or `<type>/BORE-N-<slug>` when the work has an itsaplan card (see [itsaplan](#itsaplan)).
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

## itsaplan

From Sprint 17b the work of Borealis is tracked in itsaplan, the project `ignis-dev.BORE`, and new work is not filed as GitHub issues. The main session creates the cards and moves them across a merge; each subagent touches only the card its brief names.

- **A card first.** Every piece of work that will commit has a card: a Feature, Bug, Task, Tech debt, Research or Phase card, written from its type's template, with the acceptance criteria as a checklist. A sprint or phase is a Phase card, and its tasks are its subtasks, one card each. The brief names the task's card (`BORE-N`).
- **Columns, by name.** The columns are chosen by their names, since the state types are shared by several of them. Backlog holds new and imported cards. Ready holds refined cards, and a Phase card waits there while its tasks run. In Progress holds work that has started; it has a hard limit of 4 cards per project. Integrated means the task branch is merged `--no-ff` into the integration branch. In Review means the pull request to the default branch is open. Done is set only by the merge webhook, never by hand. Canceled holds abandoned work, with a comment that says why.
- **Branches and commits.** A branch is `<type>/BORE-N-<slug>`, which links it to the card. Each commit carries a `Refs: BORE-N` trailer above its `Co-Authored-By` line, and the commit hook refuses a commit without it. A merge commit carries the trailer too.
- **Pull requests.** The body has one `Fixes BORE-N` line for each card the pull request delivers, and a Phase card is named as `Refs BORE-N` (it closes when its last task does). A card imported from a GitHub issue also gets `Fixes #N`, with the keyword directly before the number. The phase pull request lists only the cards that are Integrated. `Refs BORE-N` for the Phase card goes in only when the pull request is marked ready: the webhook moves any cited card that is in Ready or Backlog to In Review when the pull request opens.
- **Card moves.** An implementer moves its card to In Progress before its first edit. A refused move (the WIP limit, or an error from itsaplan) means stop and report, not retry in a loop. The session moves the card to Integrated when the task is merged, and to In Review when the pull request opens, with a short comment on each card that gives the result, the checks and the revision. The session moves the card of a failed, blocked or partial task back to Ready, with a comment that says what is left and why, or links the card `blocked_by` what it waits for, so it does not hold a WIP slot. The implementer only reports the state.
- **Work found on the way.** A bug, follow-up or piece of tech debt found during a task becomes a Backlog card labelled `found-by-agent`, and the report cites it. Nobody creates GitHub issues for it.
- **GitHub issues.** The session-start hook imports open issues as Backlog cards, labelled `from-github`, with the field `GitHub issue`, and comments the card link on each issue. A card that is canceled after such an import closes its issue as not planned, with the same reason. No one closes an imported issue otherwise.
- **After a merge.** Check that the cards reached Done. A card that did not usually lacks a closing word in the pull request. Fix that, and say so before a card is moved by hand.

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
- **One Gradle run at a time, also across tasks.** On 2026-10-29 a Gradle daemon died from a Windows paging-file shortage with three builds at once, and under load the accessibility tests of `:borealis:library` and `:borealis:settings` ran out of memory. Since then every run uses `--max-workers=2`, and only one runs at a time on the machine. This replaces the Sprint 10 bullet "Several builds at once". Tasks that build in parallel share one lock folder, `gradle.lock` at the root of the session scratch area (the one thing at that root an agent may create and remove): `until mkdir "$L" 2>/dev/null; do sleep 30; done; trap 'rmdir "$L"' EXIT`, then the Gradle command. An agent never removes a lock it did not take; when no Gradle runs and a lock is left, the orchestrator removes it. (Superseded in Sprint 17b by the memory-aware slots; see [Lessons from Sprint 17b](#lessons-from-sprint-17b).)
- **Gate the phase after merging parallel tasks.** Two tasks that each pass their gates can still break each other: in the UAT wave one task removed a type that the other task's new tests used. Run both gates on the phase head after the last merge of a wave, and fix such a break on the phase with its own commit. (Superseded in Sprint 17b: the full gate runs on CI; see [Lessons from Sprint 17b](#lessons-from-sprint-17b).)
- **Git Bash rewrites arguments that start with `/`.** `gh issue create --title "/cancel …"` became `C:/Program Files/Git/cancel …`, and `adb shell … /sdcard/…` paths broke the same way. Set `MSYS_NO_PATHCONV=1` for those commands, and pass local files to Windows programs with `cygpath -w`.
- **Read a device screen only when it is safe.** Before a `uiautomator dump`, check that the device is unlocked and that Borealis is in front: a dump of the S22 lock screen read the owner's notifications (the file was deleted at once). Never dump the S22 while a landscape activity is up; drive the stream with key events.
- **Drop a stream without losing adb.** Block only the app's network (`cmd connectivity set-chain3-enabled true` and `set-package-networking-enabled false <package>`; runbook 3.29, step 7), and restore both right after.
- **Wireless adb on the S22.** It stops advertising when the phone sleeps. Find it again with `adb mdns services` and `adb connect`; when it is gone, the owner turns wireless debugging back on or plugs in USB.
- **Filing in LuminalShine.** Blank issues are off there. A bug follows the bug form: Bug Symptoms (with the steps to reproduce inside it), Expected Behavior, Windows Version, and the optional Logs and Screenshot. A crash goes to the Report a Crash form, with the crash bundle. A feature request goes to Discussions (Ideas). Links to host source are pinned to a release commit, and nothing is filed without the owner's OK.

## Lessons from Sprint 16

- **Batch sessions start fresh.** Sprint 16 ran six batches in parallel, each as a session that dispatched its own `br-implementer` and `br-reviewer`, with the main session merging. Sessions forked from the orchestrator carry its whole context; two of them drifted into Spanish in their messages, a sign of a degraded context. Start each batch as a fresh session with a self-contained prompt (the plan, the brief and rules files, the worktree, the Memtrace, MemFleet and Gradle-lock lines), write every brief in English, and have the session re-read its brief and progress file from disk before each decision.
- **A merge review per batch, on a merged tree.** Before each merge, the main session ran an independent `br-reviewer` that checked every entry against the brief (not against the batch's report), the test-before-fix order, invented facts, scope and language, and ran the affected tests on a tree merged with the phase in a detached worktree. Those reviews found three cross-batch breaks that each batch's own gate could not see (a test counting a read that another batch removed, test pads without the controller number that another batch now required, a comment made false by another batch) and a regression of one batch's own fix. Fix such a break on the phase with its own commit.
- **When sessions can start.** The app starts or proposes a session only from a turn that the owner typed in the desktop app, not from a turn that an event or another session began, and not while the owner uses Remote Control. Start the later batches early in "wait for START" mode, then send START when a slot opens.
- **"Closes" lists.** "Closes #108, #109, #110" in a pull request closes only #108. Write "Closes #n" for each issue, or close the rest by hand after the merge.
- **markdownlint MD018.** A wrapped line that starts with an issue number (`#227, …`) reads as a heading without a space, and the Docs check fails. Wrap so that no line starts with `#`.
- **No whole-disk searches.** Two reviewers ran `find /` and left it running in the background. Agents search the repository, the SDK and the reference checkouts only; the orchestrator stops any such process an agent left.
- **Security findings in LuminalShine.** A finding with a security side (pairing, authentication, client input, memory contents) goes only to the host's private vulnerability report, prepared with the owner, never to a public issue, comment or discussion. Crash dumps are not posted either (owner, 2026-10-30), which replaces "with the crash bundle" in the Sprint 15 bullet on filing.
- **Long-path folders.** A worktree folder whose `build/` holds paths over 260 characters can stay behind after `git worktree remove`. The hook now blocks deleting it with a `\\?\` path, so the orchestrator names the folder for the owner to delete.

## Lessons from Sprint 17a

- **One orchestrator, `/phase-loop`, two implementers.** Sprint 17a had about 17 code items in four batches, so it
  ran in the main session with `/phase-loop` (two tasks at once on disjoint files) and no extra sessions (owner,
  2026-11-02: "only use extra sessions when it pays off"). Each batch was one task; follow-ups that a review or the
  round found became small tasks of their own on the same worktree or a fresh one.
- **Measure before you fix by assumption.** The plan asked the host for continuous audio. The before-and-after
  measurement showed it made the drops worse, and a four-build matrix on the TV (with and without it, at the old and
  a larger drop limit) showed the limit was the cause. Keep a quick spike build (a detached worktree, no commits)
  for such a matrix, and bring the table to the owner before the code changes.
- **Test the client against the host's open pull requests.** The portable "Nightly (Windows)" artifact of a
  LuminalShine pull request installs over the release with an elevated script that keeps `<name>.26.10.1.bak` copies
  and a rollback script. Two client defects showed only on those builds: the graceful quit end of #232 and the Quit
  of the device that started a shared app under #227. Ask before each download, let the owner accept each UAC prompt,
  and roll back at the end of the round.
- **Name every role in a shared flow.** The shared-Quit inference was first limited to the device that joined the
  app, on an assumption about the host's rule. The advisor's check before the pull request asked about the device
  that started it, and the host build showed the same case there. When a host rule decides by "another client is
  live", test the client as each of the clients.
- **A pairing test can unpair other devices.** The Web UI unpair of the pairing-retry test removed the TV too; pair
  it again before the next two-device step.
- **Drive the devices with small helpers, and check the screen first.** A shell file of helpers (open the Library
  from a shortcut intent, tap a text or the first card, read the foreground activity, open the menu and quit)
  replaced ad hoc taps. The host's app cards have no text in the accessibility tree, so the helper taps the first
  large clickable node; check that a stream really started before the PC plays a test sound, or the sound plays on
  the PC.
- **Keep the S22 awake only during a round.** `svc power stayon` with the power source the phone is on (`ac`, `usb`
  or `true`) kept it from locking; restore it to its earlier value (`false`) at the end. A secure lock still needs
  the owner.

## Lessons from Sprint 17b

- **CI on the draft phase pull request is the full gate.** Sprint 17b ran the full Android gate, the Core job, the
  screenshot job and the Docs job, and the Roborazzi verify on GitHub Actions, on the draft phase pull request. The
  tasks and their reviewers ran only the checks of what they touched, and no integration gate ran on the machine.
  One push goes per merge wave, about 30 minutes apart. Before BORE-38, a new push cancelled the run in progress;
  now a new run is queued, and the main session cancels an out-of-date run by hand. When a wave has two
  integration branches and a test fails, rerun the failing test on each branch to find the one that breaks it.
  This replaces the Sprint 15 bullet on gating the phase after a merge.
- **Memory-aware Gradle slots.** A Gradle run takes one of up to three lock slots. Slots two and three open only
  while the machine has at least 10 GB of commit and 6 GB of RAM free, because each extra build takes about 5 to
  6 GB. A slot records the process of the shell that took it, and a slot whose holder is gone is freed when the next
  run takes a slot. A slot is never removed by hand unless its holder is known to be gone: a slot was left by a
  run that was probably killed before its exit trap, and it was freed by hand once (2026-11-03). This replaces the Sprint 15
  bullet on one Gradle run at a time.
- **Two fresh sessions, each with its own integration branch.** Two sessions ran in parallel: one for the Screens
  lanes and one for the Controls lane. Each had its own integration branch cut from the phase branch, its own
  implementers and reviewers, and no shared files. The sessions were started fresh from a turn the owner typed, with
  a self-contained prompt, and not forked from the orchestrator. The main session merged each integration branch
  into the phase after its own merge review, and ran no integration gate on the machine. The phase branch was touched only by the main session.
- **Contracts before parallel tasks.** When tasks share an interface, the interface is written in the plan and in
  the briefs before the wave starts. The Native touch mode was the example: the setting, the menu segment and the
  send path (N1a, N1b and N2) shared a mode name, a refusal notice and a host rule. The notice text and the purpose
  line were fixed by the owner first, and each brief named them.
- **A blocked task is split, not retried.** The two-finger window (T2) came back blocked: one window for both lift
  orders needed state that the touch contexts share, which the task could not reach alone. The main session split
  it into two tasks with one card each (the shared window, then the movement check), in sequence, instead of
  sending the same brief again. The Native menu row (N1a) was blocked twice on the phone's panel width. The owner
  asked for a wider panel on a phone, and the main session set the phone panel to grow with the text up to three
  quarters of the parent width (the two thirds in the brief was the main session's own number, not the owner's).
  The change went in as its own commit.
- **Read the device before the card is written.** The device reads R1 to R6 ran before the tasks that depended on
  them. Two of the reads changed a card: R5 did not reproduce the "Nothing running" header, so BORE-5 was written as
  a test first, with a narrow change (an unknown host's header says it is checking), and R6 showed that the menu
  segments do report their state, so BORE-15 pins it and finds no defect. Reads need no card of their own.
- **Owner decisions are asked with options.** A decision the owner had to take (the hint text of the mouse chord, the
  name of the Native mode, the audio start burst, the Try again question, the Nintendo pad type) was asked with its
  options, the recommended one first, and recorded in the ledger before the task that followed. A card that the
  owner cancels after a measurement (BORE-31) moves to Canceled with a comment, and its question goes to Backlog as
  research.
- **The WIP limit is shared.** A card stays In Progress until its merge, so a call that covers several tasks holds
  several slots at once, and the limit of 4 is shared by the sessions of the project. Run one task at a time.
- **Memtrace on the phase.** The phase label could not be indexed (the job failed on the graph publication lock, and
  the daemon's watcher lacked memory for the phase delta). The agents therefore queried the `main` label, whose code
  is the phase base, and read the current source of their worktree with Grep and reads. Task worktrees had no
  overlays, so no session sent an index job, which avoided the `busy` and `os error 33` answers.
- **Bisect hazards stay in history.** With `--no-ff` merges, a few commits fail a check that the next commit fixes
  (a line-length and a class-size rule of detekt, and a spotless failure on comment lines). Each one is named in the
  review notes, so a bisect can skip it.

## Reporting to the owner

The orchestrator reports, per phase and honestly: behaviour changed, commits and their test-first order, checks run and the exact revision tested, remaining limitations and open decisions, and any blocked publication. It asks before any push to `main`, any repository setting change and any message to other people.
