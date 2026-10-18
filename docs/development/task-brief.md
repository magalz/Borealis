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

## Acceptance
- Behaviour scenarios that must hold, each expressed as a test that must exist and pass.
- Removals or documentation outcomes, stated so a reviewer can check them.

## Out of scope
What not to touch or decide.

## Verification
Exact commands to run (for example `./gradlew testDebugUnitTest` from `apps/android`, or `ctest --preset dev` from `core`).
Inherited-code rule: the implementer lists every inherited file it touched and confirms the edits are wiring only, new logic is in Kotlin and nothing was reformatted wholesale; the reviewer re-checks this.

## Notes
Known pitfalls, related files, and the order of commits when it matters.
```

The implementer's final report follows the format in `.claude/agents/br-implementer.md`. The reviewer reports with the format in `.claude/agents/br-reviewer.md`. A `br-scribe` brief names the text files to write (or the text to return), the source notes, and the template to follow.
