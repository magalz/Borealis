---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0019 Record decisions as MADR ADRs and changes in a generated changelog

## Context and Problem Statement

How should Borealis record decisions and changes so that they are versioned, auditable and indexable? Decisions need their discarded alternatives recorded, and the changelog must not drift from the actual commits.

## Considered Options

* ADRs in MADR format in `docs/adr` + `CHANGELOG.md` (Keep a Changelog) generated from Conventional Commits with git-cliff, plus Memtrace Cortex decision memory, use cases in `docs/use-cases` and the host contract in `docs/host-contract`
* Light ADRs with a manual changelog
* GitHub Wiki with release notes
* Issues and PRs only

## Decision Outcome

Chosen option: "MADR ADRs in `docs/adr` + generated changelog", because the ADRs record the discarded alternatives, and the changelog is generated from Conventional Commits so it does not drift. ADR statuses are proposed, accepted or superseded. Memtrace Cortex decision memory indexes the ADRs and standing rules, confirmed with `memtrace govern`.

### Consequences

* Good, because discarded alternatives are recorded with their pros and cons.
* Good, because the changelog is generated and does not drift.
* Good, because decisions are versioned in the repository and can be indexed by Memtrace Cortex.
* Bad, because ADRs and the changelog need to be maintained as part of each change.

## Pros and Cons of the Options

### MADR ADRs + generated changelog

* Good, because discarded alternatives are recorded.
* Good, because the changelog does not drift.
* Good, because decisions are versioned with the code and indexable.
* Bad, because the records must be maintained with each change.

### Light ADRs with a manual changelog

* Good, because it means less text.
* Bad, because discarded alternatives are lost.
* Bad, because the changelog drifts.

### GitHub Wiki with release notes

* Good, because editing is easy.
* Bad, because it is outside code versioning and PRs.

### Issues and PRs only

* Good, because overhead is zero.
* Bad, because decisions are scattered and have no audit value.

## More Information

See [0018](0018-language-of-repository-content.md), [0039](0039-changelog-with-git-cliff.md) and [0025](0025-review-and-ai-assisted-development-policy.md).
