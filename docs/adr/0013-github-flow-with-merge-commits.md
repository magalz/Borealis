---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0013 Use GitHub Flow with merge commits and Conventional Commits

## Context and Problem Statement

Which Git branching and merge workflow should Borealis use? The workflow must preserve test-first evidence (RED test commits) and fit with upstream Moonlight merges. Commit messages must also be machine-readable.

## Considered Options

* GitHub Flow with merge commits and Conventional Commits messages
* Trunk-based development with short PRs and squash merges
* GitFlow
* Trunk with release branches

## Decision Outcome

Chosen option: "GitHub Flow with merge commits and Conventional Commits", because the history of each PR is preserved and RED test commits stay visible.

Detail (from the agent organization, see [0027](0027-agent-organization.md)): one phase equals one integration PR. Task branches merge into the phase branch with `--no-ff`.

### Consequences

* Good, because the history of each PR is preserved and RED test commits stay visible.
* Good, because Conventional Commits messages are machine-readable (see [0039](0039-changelog-with-git-cliff.md)).
* Bad, because the history is non-linear and noisier next to upstream merges.

## Pros and Cons of the Options

### GitHub Flow with merge commits

* Good, because the history of each PR is preserved.
* Good, because RED test commits stay visible.
* Bad, because the history is non-linear.
* Bad, because it is noisier next to upstream merges.

### Trunk-based development with short PRs and squash merges

* Good, because the history is linear.
* Bad, because it loses test-first commit evidence.

### GitFlow

* Good, because it separates releases formally.
* Bad, because it is heavy, and long-lived branches conflict with upstream merges.

### Trunk with release branches

* Good, because published versions can be patched.
* Bad, because fixes need cherry-picks.

## More Information

See [0014](0014-semver-per-app-with-prefixed-tags.md), [0025](0025-review-and-ai-assisted-development-policy.md) and [0027](0027-agent-organization.md).
