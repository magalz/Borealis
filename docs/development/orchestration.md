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

Memtrace indexes the orchestrator's checkout, so it stays on the phase branch while a phase runs (integration happens there; the phase branch is not checked out in another worktree). Treat its answers as phase-branch state and read the file in your own worktree before editing it. For a task's own changes the orchestrator indexes the task worktree as a Memtrace overlay and subagents pass `worktree` to `find_code` (see `AGENTS.md`).

## Roles

- **Orchestrator**: plans phases, writes one [brief](task-brief.md) per task, creates worktrees, dispatches subagents, integrates reviewed work, runs phase checks, opens the phase PR under the owner's account, and reports to the owner. It owns shared files (Gradle settings and version catalog, CI workflows, `.gitmodules`, `AGENTS.md`) and decides conflicts. It sends discovery to `br-scout` instead of reading broadly itself.
- **`br-scout`**: answers "where, what calls, what breaks" questions with references and short summaries; never edits.
- **`br-scribe`**: writes text from the orchestrator's notes and repository facts; edits only the text files its brief names.
- **`br-implementer`**: implements exactly one brief inside its worktree, test-first per the [TDD rule](tdd.md), and returns a report. It never pushes, opens PRs, or touches files it does not own; if the brief is wrong it stops and reports.
- **`br-reviewer`**: given the same brief, independently checks the branch for correctness, scope, upstream rules, and test-before-implementation commit order. It reports findings but does not fix them.

## Task lifecycle

1. Brief: goal, owned files, consumed contracts, acceptance, verification.
2. Worktree at `D:\Repos\Borealis-worktrees\<phase>-<task>` and branch `task/<phase>-<task>` created from the phase branch `phase/<n>-<name>`.
3. The orchestrator indexes the worktree as a Memtrace overlay when the task needs code discovery.
4. `br-implementer` works test-first and runs the checks from the brief.
5. `br-reviewer` reviews. Blocking findings go back to the implementer for at most two fix rounds; then the orchestrator decides, or escalates to the owner.
6. The orchestrator merges the task branch into the phase branch with `--no-ff`.
7. When the phase's tasks are merged, the orchestrator runs the phase checks on the final revision.
8. `br-scribe` drafts the PR description; the orchestrator opens one PR for the phase with the owner's GitHub account.
9. The owner reviews and merges it with a merge commit, so test-first commits are preserved. Agents never merge.

## Parallel waves

Tasks without shared files and with satisfied dependencies run in parallel in one wave. Shared declarations (Gradle settings, version catalog, CI, `.gitmodules`, upstream hook points) have exactly one owner per wave; a task needing such a file it does not own stops and reports. A later wave starts only after its dependencies are merged into the phase branch, or when it depends only on a frozen contract document.

## Reporting to the owner

The orchestrator reports, per phase and honestly: behaviour changed, commits and their test-first order, checks run and the exact revision tested, remaining limitations and open decisions, and any blocked publication. It asks before any push to `main`, any repository setting change and any message to other people.
