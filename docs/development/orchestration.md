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

Memtrace holds the graph of the orchestrator's checkout, which stays on the phase branch while a phase runs (integration happens there; the phase branch is not checked out in another worktree). The orchestrator indexes it incrementally with `branch: "main"` and re-runs that incremental index after each merge wave. It does not call `watch_directory`: it labels changes with the checked-out branch, which would split the labels again. `memtrace start` arms its own watcher under the checked-out branch, so answers for `branch: "main"` reflect the last incremental index of the checkout, not live edits. The label is only a name: treat answers as phase-branch state and read the file in your own worktree before editing it. Graph and quality queries always pass `branch: "main"`: Memtrace (v1.2.8) reuses unchanged nodes across branch labels, its quality tools match the exact label, and indexing a worktree once overwrote the repository record's branch. A task's own changes are an overlay with its task branch: the orchestrator indexes the task worktree and subagents pass `worktree` to `find_code` (see `AGENTS.md`).

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
2. Worktree at `D:\Repos\Borealis-worktrees\<phase>-<task>` and branch `task/<phase>-<task>` created from the phase branch `phase/s<n>-<name>` (for example `task/s8-docs-guides` from `phase/s8-tidy-up`).
3. The orchestrator indexes the worktree as a Memtrace overlay with the task branch when the task needs code discovery; graph and quality queries stay on `branch: "main"`.
4. `br-implementer` works test-first and runs the checks from the brief.
5. `br-reviewer` reviews. Blocking findings go back to the implementer for at most two fix rounds; then the orchestrator decides, or escalates to the owner.
6. The orchestrator merges the task branch into the phase branch with `--no-ff`.
7. When the phase's tasks are merged, the orchestrator runs the phase checks on the final revision.
8. `br-scribe` drafts the PR description; the orchestrator pushes the task and phase branches and opens one PR for the phase with the owner's GitHub account. Subagents never push or open PRs.
9. The owner reviews and merges it with a merge commit, so test-first commits are preserved. Agents never merge.
10. After the owner merges, the orchestrator cleans up:
    - `git worktree remove` for each task worktree, then `git worktree prune`;
    - deletes the task branches, local and remote;
    - runs the Memtrace `cleanup_worktrees` tool for `repo_id: Borealis`, which drops the overlays of the removed worktrees;
    - re-indexes `main` incrementally.

## Parallel waves

Tasks without shared files and with satisfied dependencies run in parallel in one wave. Shared declarations (Gradle settings, version catalog, CI, the wiring in inherited files) have exactly one owner per wave; a task needing such a file it does not own stops and reports. A later wave starts only after its dependencies are merged into the phase branch, or when it depends only on a frozen contract document.

## Reporting to the owner

The orchestrator reports, per phase and honestly: behaviour changed, commits and their test-first order, checks run and the exact revision tested, remaining limitations and open decisions, and any blocked publication. It asks before any push to `main`, any repository setting change and any message to other people.
