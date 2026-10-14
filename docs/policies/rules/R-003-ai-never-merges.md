# R-003 Every change goes through a pull request; AI agents never merge

- Every change reaches `main` through a pull request with a green `CI result` check.
- Pull requests are opened with the owner's own GitHub account; there is no bot.
- AI agents never merge, never push to `main` and never change repository settings without the owner's explicit confirmation. Only the owner merges, with a merge commit.

See [ADR 0025](../../adr/0025-review-and-ai-assisted-development-policy.md) and [AI-assisted development](../ai-assisted-development.md).
