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
- **Index:** `repo_id: "Borealis"`, `branch: "<phase branch>"`, at `<phase head sha>` (indexed <date and time>). Pass `repo_id` on every call. Pass `branch: "<phase branch>"` on every tool that takes a branch (`get_symbol_context`, `analyze_relationships`, `get_impact`, `detect_changes` and the quality tools); a call without it mixes older branch labels and can come back ambiguous, empty or slow. `find_symbol`, `find_code` and `preflight_check` take no branch. When two symbols share a name, pass `file_path` or the `node_id` from an earlier result.
- **Your overlay:** pass `worktree: "Borealis:<worktree folder>"` to `find_code` when `list_worktrees` lists it. It shows your uncommitted edits; the graph tools do not.
- **Paths:** Memtrace returns `d:/Repos/Borealis/<path>`. Replace that prefix with your worktree root before you read or edit.
- **Order:** `find_symbol` (exact name) or `find_code` to locate; `get_symbol_context` for callers and callees; `get_impact` with `direction: "upstream"` and `depth: 2` for the blast radius before you edit a symbol this task did not write (`preflight_check` can return a very large answer; use it on small symbols); `detect_changes` at the end with your unified diff as `diff` (a list of relative paths came back empty).
- **An empty result is not proof that code is absent.** Check the `repo_id`, `branch` and `worktree` you passed, retry once with the exact name or a broader query, then use Grep or a ranged read.
- **Not in the graph:** `**/*.gradle.kts`, the design tokens, the prebuilt OpenSSL and Opus headers and `core/third_party/`. Use Grep there.
- **Report** the Memtrace calls you made (tool, key parameters, empty or not), where you fell back to Grep, and two counts: Memtrace calls, and file reads plus Grep calls.

## Acceptance
- Behaviour scenarios that must hold, each expressed as a test that must exist and pass.
- Removals or documentation outcomes, stated so a reviewer can check them.

## Out of scope
What not to touch or decide.

## Verification
Exact commands to run (for example `./gradlew testDebugUnitTest` from `apps/android`, or `ctest --preset dev` from `core`).
Inherited-code rule: the implementer lists every inherited file it touched and confirms that no Java file was added (the app has no Java since Sprint 14), that new logic is in Kotlin and that nothing was reformatted wholesale. Vendored files under `core/third_party/` change only when the brief says so, and each change is recorded in `core/third_party/README.md` as that README describes. The reviewer re-checks this.

## Notes
Memtrace: `repo_id: "Borealis"`, `branch: "<phase branch>"` at `<phase head sha>`; overlay `Borealis:<worktree folder>` or none. Use it first, and count your Memtrace calls against your file reads and Grep calls in the report.
Then known pitfalls, related files, and the order of commits when it matters.
```

The orchestrator fills the Memtrace section in every brief for `br-implementer`, `br-reviewer`, `br-scout` and the Explore agents, after it has indexed the checkout and the task worktree (see [orchestration](orchestration.md)). `br-scribe` has no Memtrace tools and gets the facts in its brief.

The implementer's final report follows the format in `.claude/agents/br-implementer.md`. The reviewer reports with the format in `.claude/agents/br-reviewer.md`. A `br-scribe` brief names the text files to write (or the text to return), the source notes, and the template to follow.
