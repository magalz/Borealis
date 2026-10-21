# Task brief template

The orchestrator writes one brief per task and sends it to the `br-implementer` subagent. The same brief is given to `br-reviewer`. Keep it short: a brief that needs more than a page usually describes more than one task.

```markdown
# <Phase>-<Task>: <title>

**Worktree:** D:/Repos/Borealis-worktrees/<phase>-<task> (branch `task/<phase>-<task>`, based on `<base sha>`)

## Goal
One paragraph: the outcome and why it matters.

## Owns
Files and directories this task may create, change, or delete.

## Consumes
Interfaces, documents, and decisions this task relies on (with paths). Do not change them; report if they are wrong.

## Memtrace
Load the `memtrace-skills:memtrace-first` skill before you explore code.
- **Index:** `repo_id: "Borealis"`, `branch: "main"`, at `<phase head sha>` (indexed <date and time>). Pass both on every graph, impact and quality call (`find_symbol`, `get_symbol_context`, `analyze_relationships`, `get_impact`, `preflight_check`, `detect_changes`). A call without the branch can come back empty.
- **Your overlay:** pass `worktree: "Borealis:<worktree folder>"` to `find_code`. It shows your uncommitted edits; the graph tools do not.
- **Paths:** Memtrace returns `d:/Repos/Borealis/<path>`. Replace that prefix with your worktree root before you read or edit.
- **Order:** `find_symbol` (exact name) or `find_code` to locate; `get_symbol_context` for callers and callees; `preflight_check` before you edit a symbol this task did not write; `get_impact` with `direction: "upstream"` for the blast radius; `detect_changes` with your diff at the end.
- **An empty result is not proof that code is absent.** Check the `repo_id`, `branch` and `worktree` you passed, retry once with the exact name or a broader query, then use Grep or a ranged read.
- **Not in the graph:** `**/*.gradle.kts`, the design tokens, the prebuilt OpenSSL and Opus headers and `core/third_party/`. Use Grep there.
- **Report** the Memtrace calls you made (tool, key parameters, empty or not) and where you fell back to Grep.

## Acceptance
- Behaviour scenarios that must hold, each expressed as a test that must exist and pass.
- Removals or documentation outcomes, stated so a reviewer can check them.

## Out of scope
What not to touch or decide.

## Verification
Exact commands to run (for example `./gradlew testDebugUnitTest` from `apps/android`, or `ctest --preset dev` from `core`).
Inherited-code rule: the implementer lists every inherited file it touched and confirms the edits to inherited Java are wiring only, new logic is in Kotlin (migrating an area to Kotlin is allowed when the brief says so) and nothing was reformatted wholesale. Vendored files under `core/third_party/` change only when the brief says so, and each change is recorded in `core/third_party/README.md` as that README describes. The reviewer re-checks this.

## Notes
Known pitfalls, related files, and the order of commits when it matters.
```

The orchestrator fills the Memtrace section in every brief for `br-implementer`, `br-reviewer`, `br-scout` and the Explore agents, after it has indexed the checkout and the task worktree (see [orchestration](orchestration.md)). `br-scribe` has no Memtrace tools and gets the facts in its brief.

The implementer's final report follows the format in `.claude/agents/br-implementer.md`. The reviewer reports with the format in `.claude/agents/br-reviewer.md`. A `br-scribe` brief names the text files to write (or the text to return), the source notes, and the template to follow.
