---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0039 Generate the changelog with git-cliff

## Context and Problem Statement

How should `CHANGELOG.md` be produced, given that commits follow Conventional Commits (see [0013](0013-github-flow-with-merge-commits.md)) and the changelog must not drift (see [0019](0019-decision-and-change-records.md))?

## Considered Options

* git-cliff (from Conventional Commits)
* release-please
* semantic-release
* Manual changelog

## Decision Outcome

Chosen option: "git-cliff", because it is a single binary that generates the changelog from Conventional Commits. The trade-off is a TOML configuration.

### Consequences

* Good, because it is a single binary generated from Conventional Commits.
* Bad, because it needs a TOML configuration.

## Pros and Cons of the Options

### git-cliff

* Good, because it is a single binary and generates from Conventional Commits.
* Bad, because it needs a TOML configuration.

### release-please

* Good, because it automates release PRs.
* Bad, because it is opinionated.

### semantic-release

* Good, because it is complete.
* Bad, because it needs Node.

### Manual changelog

* Good, because the prose is better.
* Bad, because it drifts.

## More Information

See [0014](0014-semver-per-app-with-prefixed-tags.md) and [0019](0019-decision-and-change-records.md).
