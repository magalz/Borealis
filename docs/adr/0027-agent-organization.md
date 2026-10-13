---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0027 Organize AI agents as an orchestrator with specialized br-* roles

## Context and Problem Statement

Borealis is developed with AI agents. How should the agents be organized, which model should each role use, and what is the lifecycle of a task? The organization is copied from SuiteWard and adapted.

## Considered Options

* Opus 5.5 orchestrator with `br-implementer` (Sonnet 5.5), `br-scout` (Haiku 5.5), `br-scribe` (Haiku 5.5) and `br-reviewer` (Sonnet 5.5)
* SuiteWard original: Sonnet orchestrator with an Opus advisor
* Single agent doing everything

## Decision Outcome

Chosen option: "Opus 5.5 orchestrator with specialized br-* roles", because the orchestrator should be the strongest model, according to the owner.

Roles:

* **Orchestrator** (Opus 5.5, main session): plans, briefs, creates worktrees, dispatches, integrates, runs phase checks, publishes, and reports. It owns shared files.
* **br-implementer** (Sonnet 5.5, high effort): one task per worktree, TDD, never pushes and never opens PRs.
* **br-scout** (Haiku 5.5): read-only exploration, Memtrace first.
* **br-scribe** (Haiku 5.5): PR text, release notes, changelog, issue text and ADRs from orchestrator notes. Never writes code and never publishes.
* **br-reviewer** (Sonnet 5.5, high effort): independent read-only review against the brief.

Left out from SuiteWard: the Orbit executor, the ponytail plugin, Go, gopls and Podman specifics, and the GitHub App bot.

Lifecycle: brief, then a worktree at `D:\Repos\Borealis-worktrees\<phase>-<task>` on branch `task/<phase>-<task>` from `phase/<n>-<name>`, then the implementer (TDD), then the reviewer (at most 2 fix rounds), then a `--no-ff` merge into the phase branch, then phase checks, then the scribe drafts the PR text, then the orchestrator opens one PR per phase under the owner's account, and the owner merges with a merge commit.

### Consequences

* Good, because the orchestrator uses the strongest model, and cheaper roles handle exploration and text.
* Good, because each task runs in its own worktree, with independent review before merge.
* Bad, because several roles and worktrees must be coordinated.

## Pros and Cons of the Options

### Opus 5.5 orchestrator with br-* roles

* Good, because the orchestrator uses the strongest model, according to the owner.
* Good, because cheaper roles handle exploration and text, and tasks run in parallel in worktrees.
* Good, because reviews are independent of the implementer.
* Bad, because roles and worktrees must be coordinated.

### SuiteWard original (Sonnet orchestrator with an Opus advisor)

* Good, because orchestration is cheaper.
* Bad, because the orchestrator should be the strongest model, according to the owner.

### Single agent doing everything

* Good, because it is simple.
* Bad, because it is expensive and less parallel.

## More Information

See [0013](0013-github-flow-with-merge-commits.md), [0025](0025-review-and-ai-assisted-development-policy.md) and [0019](0019-decision-and-change-records.md).
