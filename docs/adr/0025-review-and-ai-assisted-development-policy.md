---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0025 Require PRs with green CI and let only the owner merge, with AI assistance

## Context and Problem Statement

How should changes be reviewed and merged, given that AI tools assist development? GitHub does not allow approving one's own PR, so a formal approval cannot come from the owner's own PRs. Should AI agents or bots open, review or merge PRs?

## Considered Options

* Every change through a PR with green CI; PRs opened under the owner's own GitHub account (no bot); AI reviews (Claude code review, Memtrace review) assist; the AI never merges, only the owner merges after review
* Owner approval only on critical areas (CODEOWNERS)
* Auto-merge when green
* Direct commits to `main`
* (Rejected) Publishing PRs through a GitHub App bot, as SuiteWard does, to enable formal approval

## Decision Outcome

Chosen option: "Every change through a PR with green CI, opened under the owner's account, with AI reviews assisting and only the owner merging", because it keeps the owner in control and matches the CI, audit and GitHub Flow decisions. Rulesets require a PR and status checks, but not a formal approval, because GitHub does not let the owner approve their own PR. AI commits carry `Co-Authored-By` trailers.

### Consequences

* Good, because every change passes green CI and a review, and the owner stays in control.
* Good, because PRs opened under the owner's own account and merged only by the owner keep history attributable to one accountable person.
* Bad, because rulesets cannot require a formal approval, so the protection rests on status checks and the owner's review.

## Pros and Cons of the Options

### Every change through a PR, owner merges after AI-assisted review

* Good, because the owner keeps control over merges.
* Good, because CI and AI reviews run on every PR.
* Bad, because a formal approval cannot be required from the owner's own PR.

### Owner approval only on critical areas (CODEOWNERS)

* Good, because non-critical changes merge faster.
* Bad, because non-critical changes merge without human review.

### Auto-merge when green

* Good, because it is the fastest route.
* Bad, because the owner has no control over merges.

### Direct commits to `main`

* Good, because there is no ceremony.
* Bad, because it contradicts CI, the audit trail and GitHub Flow.

### Publishing PRs through a GitHub App bot (rejected)

* Good, because it would allow a formal approval.
* Bad, because the owner chose to keep PRs under their own name.

## More Information

See [0013](0013-github-flow-with-merge-commits.md), [0019](0019-decision-and-change-records.md) and [0027](0027-agent-organization.md).
