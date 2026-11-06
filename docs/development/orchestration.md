# Orchestration

The main Claude session orchestrates Borealis work. It delegates exploration, light text work, implementation and review to subagents defined in `.claude/agents/`, and leaves mechanical decisions to hooks, scripts and CI. Models are set in the agent files and in `.claude/settings.json`. This organization is adapted from the SuiteWard project and recorded in the ADRs (see the [ADR index](../adr/README.md)).

The shared agent workflow (the task lifecycle, parallel waves, the itsaplan flow, worktree cleanup, safe deletion, the failing commit with its stub, the checks after each merge wave, and reporting) is in the owner's global `~/.claude/AGENTS.md`. This guide keeps only what is specific to Borealis.

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

- **Orchestrator**: plans phases, writes one [brief](task-brief.md) per task, creates worktrees, dispatches subagents, integrates reviewed work, runs phase checks, pushes the task and phase branches, opens the phase PR under the owner's account, and reports to the owner. It owns shared files (Gradle settings and version catalog, CI workflows, the wiring in inherited files, `AGENTS.md`) and decides conflicts. It sends discovery to `br-scout` instead of reading broadly itself.
- **`br-scout`**: answers "where, what calls, what breaks" questions with references and short summaries; never edits.
- **`br-scribe`**: writes text from the orchestrator's notes and repository facts; edits only the text files its brief names.
- **`br-implementer`**: implements exactly one brief inside its worktree, test-first per the [TDD rule](tdd.md), and returns a report. It never pushes, opens PRs, or touches files it does not own; if the brief is wrong it stops and reports.
- **`br-reviewer`**: given the same brief, independently checks the branch for correctness, scope, inherited-code rules, and test-before-implementation commit order. It reports findings but does not fix them.

## Task lifecycle

The steps from the brief to the phase pull request are in the global `~/.claude/AGENTS.md`. What Borealis adds:

- **Worktree and branch.** A task worktree is `D:\Repos\Borealis-worktrees\<phase>-<task>`, on a branch cut from the phase branch `phase/s<n>-<name>` (for example `phase/s8-tidy-up`) and named after the task's card, `<type>/BORE-N-<slug>` (see [itsaplan](#itsaplan)). Before itsaplan, task branches were `task/<phase>-<task>` (for example `task/s8-docs-guides`).
- **Memtrace overlay.** The orchestrator indexes the worktree as a Memtrace overlay with the task branch when the task needs code discovery; graph and quality queries stay on the indexed phase branch.
- **Gradle before a worktree is removed.** The orchestrator stops the Gradle daemons (`./gradlew --stop` from `apps/android`, once no build runs) before `git worktree remove`, because a running daemon keeps files of a worktree's `build` folders open and `git worktree remove` then fails.
- **After the owner merges the phase**, besides the cleanup in the global `AGENTS.md`, the orchestrator:
  - runs the Memtrace `cleanup_worktrees` tool for `repo_id: Borealis`, which drops the overlays of the removed worktrees;
  - checks out `main` in the orchestrator's checkout and pulls the merge;
  - wipes the whole Borealis store, running each command in `D:\Repos\Borealis`: `memtrace stop`, then `memtrace reset` with no repository, which empties `D:\Repos\Borealis\.memdb` and moves its history to `.memdb.history`, then `memtrace history purge --store D:\Repos\Borealis\.memdb --delete-forever`, then starts the daemon again (`memtrace start --headless --no-workspace` in `D:\Repos\Borealis`, with `MEMTRACE_NO_STARTUP_WATCH=1`, `MEMTRACE_NO_WATCH_RESTORE=1` and `MEMTRACE_NO_REPLAY=1`, the last one set only in the environment that launches the daemon). This drops every record, branch label and history replay. The per-repository `memtrace reset Borealis` is not used: on a store of 28 GB its delete gave no answer within two hours (2026-10-23);
  - lets the daemon index the checkout again from scratch: on an empty store it starts that index on its own, as `main`, and an `index_directory` call meanwhile answers `peer_indexing`. It follows the job with `list_jobs` and `check_job_status`, then checks with `list_indexed_repositories` that `graph_head_sha` is the merge commit. Without `MEMTRACE_NO_REPLAY`, the daemon's first index replays the git history before it computes the embeddings, and the replay holds the store lease: `find_symbol` and `find_code` fail, no embeddings exist until it ends, and a replay cut short leaves duplicate symbols. `defer_replay: true` on `index_directory` does not prevent this, because the daemon's own index starts first (2026-10-26). The history tools lack the old commits until `replay_history` runs.

## itsaplan

From Sprint 17b the work of Borealis is tracked in the itsaplan project `ignis-dev.BORE`, with cards named `BORE-N`; GitHub issues were used before it. The flow (cards, columns, card moves, branches, commit trailers and the closing words of a pull request) is in the global `~/.claude/AGENTS.md`. In Borealis the `Refs: BORE-N` trailer goes above the `Co-Authored-By` line, and a pull request closes each card it delivers from the summary line that describes it: `- Fixes BORE-N: <what changed>`.

## Lessons from Sprint 10

- **Scratch area.** The session scratch area lives under `%TEMP%`, and the owner's monthly cleanup wipes `%TEMP%`; it was also wiped once during a run (2026-10-20), so nothing there is a record. Keep results in the repository or in the report. Never run a recursive delete on a folder that holds links or junctions.
- **Mutation tests.** To test a mutation of the code, an agent uses `git worktree add --detach` under `D:\Repos\Borealis-worktrees\zz-<name>`, and removes it with `git worktree remove --force`.
- **Gradle file locks can leave a worktree folder behind.** On Windows a Gradle daemon or an IDE can hold a file open, so `git worktree remove --force` may fail or leave the folder. Such a folder is reported by its exact path, not deleted.
- **Several builds at once.** Tasks build on the same machine in parallel. If a daemon dies for lack of memory, wait and run the command again; never kill another build's processes to free memory. On the KSP error "different roots", run the task again with `--rerun`.

## Lessons from Sprint 13

- **Mutation runs and disk space.** On 2026-10-25 mutation loops (one test JVM per mutant) left 11,469 Robolectric folders and 4,982 Conscrypt libraries in `%TEMP%` and filled the C: drive. Rules since: at most 60 mutants per task, one at a time, in the foreground (never in the background, so a loop ends with its agent); no `--fail-fast` in a mutation run; never mutate a time, a delay, a loop bound or a sleep to a large value (one mutant made a test sleep for 24 days, and its processes had to be stopped by hand); count the `robolectric-*` folders before and after a run.
- **Windows path length.** A test temporary folder deep under the checkout made Robolectric's SQLite paths pass 260 characters. The build now puts the folder at the drive root on Windows. After changing test temp paths, run the tests that open SQLite too.
- **Characterize before converting a large class.** `ControllerHandler` (3,659 lines) was pinned by three characterization tasks working in parallel on separate test packages, then converted in one task with every test unchanged, and reviewed in two halves.
- **Worktree removal on Windows.** A worktree whose `build/` holds paths over 260 characters cannot be removed by `git worktree remove` ("Invalid argument"): git unregisters it and the folder stays. In this sprint the orchestrator removed such a folder with a `\\?\` path; since Sprint 16 it names the folder for the owner to delete (see [Lessons from Sprint 16](#lessons-from-sprint-16)). The phase review confirmed the cause: 2,171 paths in that worktree's `build/` passed 260 characters.

## Lessons from Sprint 15

- **One Gradle run at a time, also across tasks.** On 2026-10-29 a Gradle daemon died from a Windows paging-file shortage with three builds at once, and under load the accessibility tests of `:borealis:library` and `:borealis:settings` ran out of memory. Since then every run uses `--max-workers=2`, and only one runs at a time on the machine. This replaces the Sprint 10 bullet "Several builds at once". Tasks that build in parallel share one lock folder, `gradle.lock` at the root of the session scratch area (the one thing at that root an agent may create and remove): `until mkdir "$L" 2>/dev/null; do sleep 30; done; trap 'rmdir "$L"' EXIT`, then the Gradle command. An agent never removes a lock it did not take; when no Gradle runs and a lock is left, the orchestrator removes it. (Superseded in Sprint 17b by the memory-aware slots; see [Lessons from Sprint 17b](#lessons-from-sprint-17b).)
- **Git Bash rewrites arguments that start with `/`.** `gh issue create --title "/cancel …"` became `C:/Program Files/Git/cancel …`, and `adb shell … /sdcard/…` paths broke the same way. Set `MSYS_NO_PATHCONV=1` for those commands, and pass local files to Windows programs with `cygpath -w`.
- **Read a device screen only when it is safe.** Before a `uiautomator dump`, check that the device is unlocked and that Borealis is in front: a dump of the S22 lock screen read the owner's notifications (the file was deleted at once). Never dump the S22 while a landscape activity is up; drive the stream with key events.
- **Drop a stream without losing adb.** Block only the app's network (`cmd connectivity set-chain3-enabled true` and `set-package-networking-enabled false <package>`; runbook 3.29, step 7), and restore both right after.
- **Wireless adb on the S22.** It stops advertising when the phone sleeps. Find it again with `adb mdns services` and `adb connect`; when it is gone, the owner turns wireless debugging back on or plugs in USB.
- **Filing in LuminalShine.** Blank issues are off there. A bug follows the bug form: Bug Symptoms (with the steps to reproduce inside it), Expected Behavior, Windows Version, and the optional Logs and Screenshot. A crash goes to the Report a Crash form, with the crash bundle. A feature request goes to Discussions (Ideas). Links to host source are pinned to a release commit, and nothing is filed without the owner's OK.

## Lessons from Sprint 16

- **Batch sessions start fresh.** Sprint 16 ran six batches in parallel, each as a session that dispatched its own `br-implementer` and `br-reviewer`, with the main session merging. Sessions forked from the orchestrator carry its whole context; two of them drifted into Spanish in their messages, a sign of a degraded context. Start each batch as a fresh session with a self-contained prompt (the plan, the brief and rules files, the worktree, the Memtrace, MemFleet and Gradle-lock lines), write every brief in English, and have the session re-read its brief and progress file from disk before each decision.
- **A merge review per batch, on a merged tree.** Before each merge, the main session ran an independent `br-reviewer` that checked every entry against the brief (not against the batch's report), the test-before-fix order, invented facts, scope and language, and ran the affected tests on a tree merged with the phase in a detached worktree. Those reviews found three cross-batch breaks that each batch's own gate could not see (a test counting a read that another batch removed, test pads without the controller number that another batch now required, a comment made false by another batch) and a regression of one batch's own fix. Fix such a break on the phase with its own commit.
- **When sessions can start.** The app starts or proposes a session only from a turn that the owner typed in the desktop app, not from a turn that an event or another session began, and not while the owner uses Remote Control. Start the later batches early in "wait for START" mode, then send START when a slot opens.
- **markdownlint MD018.** A wrapped line that starts with an issue number (`#227, …`) reads as a heading without a space, and the Docs check fails. Wrap so that no line starts with `#`.
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
- **Read the device before the card is written.** The device reads R1 to R6 ran before the tasks that depended on
  them. Two of the reads changed a card: R5 did not reproduce the "Nothing running" header, so BORE-5 was written as
  a test first, with a narrow change (an unknown host's header says it is checking), and R6 showed that the menu
  segments do report their state, so BORE-15 pins it and finds no defect. Reads need no card of their own.
- **Memtrace on the phase.** The phase label could not be indexed (the job failed on the graph publication lock, and
  the daemon's watcher lacked memory for the phase delta). The agents therefore queried the `main` label, whose code
  is the phase base, and read the current source of their worktree with Grep and reads. Task worktrees had no
  overlays, so no session sent an index job, which avoided the `busy` and `os error 33` answers.
- **Bisect hazards stay in history.** With `--no-ff` merges, a few commits fail a check that the next commit fixes
  (a line-length and a class-size rule of detekt, and a spotless failure on comment lines). Each one is named in the
  review notes, so a bisect can skip it.

## Lessons from Sprint 18a

- **A failed history replay leaves the store "indexing" for good.** On 2026-11-05 a daemon started with a plain
  `memtrace start` replayed 200 commits, the replay failed ("owned source retains extra source rows"), and every
  query answered `indexing_in_progress`, through two more plain restarts. A restart through the launcher with
  `MEMTRACE_NO_REPLAY` made the graph current, but its publication stayed "partial", and a full index then
  failed on a stale edge row. What worked was the full wipe of the [task lifecycle](#task-lifecycle) (stop,
  reset, history purge), then the daemon started again through the launcher with `MEMTRACE_NO_REPLAY`,
  `MEMTRACE_NO_STARTUP_WATCH` and `MEMTRACE_NO_WATCH_RESTORE`: the graph was rebuilt from source, and the
  embeddings finished after it. A plain restart does not clear that state.
- **A session's `memtrace mcp` keeps state of its own.** It can hold a job that does not clear (an incremental
  job of the main session and an overlay job of lane B got stuck in "scan": the first was killed, and the second
  never cleared), though an overlay job from a session's MCP can also just be slow: one finished after about 6.5
  minutes. It can hold an old quota too (below). The daemon serves MCP over HTTP at `http://127.0.0.1:3030/mcp`, and a small shell
  helper in the session scratch area (`mt.sh <tool> '<json arguments>'`, forward-slash paths; it is not in the
  repository) calls it. Every call of a task goes through the helper when the session's MCP is stuck or stale.
- **Index overlays through the daemon.** The overlay of a worktree is indexed with `index_directory` and
  `check_job_status` through the helper, never through a session's MCP: through the daemon an overlay took
  seconds, and a task may dispatch on the `main` label with no overlay when its code is the same. One overlay
  waited on `repository_busy` and succeeded on a retry.
- **The monthly quota ran out in the middle of the sprint.** On 2026-11-06 the community plan's query quota
  (13,122 queries used of 1,000 a month, resetting on 2026-12-06) was spent, and every call answered
  `quota_exceeded`, through the MCP and the daemon. The rule while it lasted: one test call and no retry loop;
  callers and impact from the LSP and Grep; a mutation check on every refactored method; the report names the gap
  ("Memtrace blocked (quota)"). When the owner added credits, the daemon kept the old quota in memory until it
  was restarted through the launcher (with `MEMTRACE_NO_REPLAY`, and no rebuild), and the session's MCP kept
  answering `quota_exceeded`, so from then on every call went through the helper.
- **Wrapped markers.** Four markers were wrapped over two comment lines (`...(Sprint 13` and, on the next line,
  `// backlog).`), so the one-line search that counted 158 markers missed four of the 162. Search with
  `rg -n -U "Sprint 1[234][ \t]*(\n[ \t]*//[ \t]*)?backlog" apps/android`, and write a relabeled marker on one
  line. A marker sweep also finds only the places that carry a marker: six suppressions in the network
  code had none. Only one of them is structural (`NvConnection.quitAndLaunch`, `ReturnCount`); the other five
  suppress `PrintStackTrace`, generic exceptions, `UNUSED_PARAMETER` and `VarCouldBeVal`. The card BORE-92 named
  all six as examples, but its scope is the structural rules only.
- **Lint was not in the task checks.** One `UseKtx` error (`SparseArray.size()` in the `ContextList.publish()` of
  BORE-90) failed the Android job on every push from `395178097` to `b98a57f49`, 9 runs, because the pushes went
  out without a look at the results. Check the CI result of each wave before the next push. `lintDebug` of every
  module a task touches is now one of the task checks, and a reviewer runs it too.
- **Hooks read the command line.** A commit message goes in `-m` arguments, with `Refs: BORE-N` and
  `Co-Authored-By` as the two lines of one last `-m`. Two `-m` flags make two paragraphs, and
  `git interpret-trailers` then reads only the last one, so the hook does not see `Refs`. Check each commit with
  `git log -1 --format=%B | git interpret-trailers --parse`. The eight commits and the merge of BORE-75 failed that
  check and were rewritten before they were published (`git filter-branch --msg-filter` over the lane, with the
  trees compared before and after), and a few other messages had `Refs` outside the trailer block and were
  reworded. The itsaplan hook reads the trailers and the closing words from the command line too: use `-m` and an
  inline `--body`, not files, and give a draft pull request its closing words as well.
- **Fixes before the refactor found more bugs than the plan listed.** Wave 0 was five cards (BORE-39, 42, 43, 54
  and 51), and BORE-89, BORE-90 and BORE-95 came out of it: the implementer of BORE-54 found BORE-89, BORE-95
  turned up while BORE-89 was fixed, and the flaky `ControllerNumberLimitTest` (BORE-62) turned out to be
  a production race on the lists of pads (BORE-90), which a load run with burner threads reproduced about once in
  1500 runs. A gate in the test rig would have turned the crash green. Reproduce a flaky test under load before
  anything is changed in it, and fix before the refactor, so that the refactor keeps no known defect: a later fix
  also reuses an earlier one (BORE-89 reuses the helpers of BORE-54).
- **Mutation checks were the strongest evidence that a refactor kept behaviour.** A characterization test is
  proven by a mutant of the old code that it kills, and the new code is proven by one-line mutants (a swapped
  order, a dropped condition, a flipped branch) that a named test catches. CH-3 ran a 54-mutant audit of the
  Sprint 13 tests and 39 mutants of the new helpers, CH-4 29 and CH-5 22, and all were caught; the reviews of the
  lane merges ran their own mutants. They also stood in for Memtrace while its quota was out.
- **A usage limit stopped the sessions in the middle of tasks.** It hit on 2026-11-06 (resetting at 04:10 local
  time). The implementer of BORE-90 died after its failing test with the fix uncommitted, one run of
  BORE-80 died with no commit, and the lane sessions stopped. They resumed from their progress files: the one of
  BORE-90 held its design, so the resumed task went on from it, and the run of BORE-80 started again from its
  brief. A progress file that holds the decisions, and not only the commits, is what makes a resume cheap.
- **Three lanes, one phase.** The main session ran lane A (`ControllerHandler`, and the library and stream UI)
  through `/phase-loop`, and two fresh sessions ran lane B (network and host) and lane C (pads and input) on
  disjoint files, each with its own integration branch, implementers and reviewers. Every lane merge into the
  phase got an independent merge review in the main session, on a merged tree in a detached worktree. Those
  reviews found what a task review cannot: a test fake of lane A that stopped compiling when BORE-42 changed the
  return type of `rumble` (fixed on the phase with its own commit), commits whose trailers did not parse, and a
  fix commit that failed detekt alone, which the notes name for a bisect. When a task needed five one-line edits
  in the test fakes of another lane, the main session allowed the return type and `return true` only, and merged
  the lane before the task that needed them.
