# R-003 Every change goes through a pull request; AI agents never merge

- Every change reaches `main` through a pull request with green required checks: `CI result`, plus `codecov/patch` and `codecov/project` ([ADR 0049](../../adr/0049-coverage-gate.md)).
- Pull requests are opened with the owner's own GitHub account; there is no bot.
- AI agents never merge, never push to `main` and never change repository settings without the owner's explicit confirmation. Only the owner merges, with a merge commit.

See [ADR 0025](../../adr/0025-review-and-ai-assisted-development-policy.md) and [AI-assisted development](../ai-assisted-development.md).
