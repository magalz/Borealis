---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0018 Write all repository content in English

## Context and Problem Statement

Which language should be used for code, comments, commits, PRs, ADRs and documentation? The owner is Portuguese-speaking, and the project aims at an international community.

## Considered Options

* Everything in English (code, comments, commits, PRs, ADRs, docs)
* Code and commits in English, internal docs in Portuguese
* Commits and docs in Portuguese
* English plus bilingual docs

## Decision Outcome

Chosen option: "Everything in English", because it makes the project ready for an international community, keeps one language, and matches Moonlight and LuminalShine.

### Consequences

* Good, because the project is ready for an international community.
* Good, because one language is used everywhere, consistent with Moonlight and LuminalShine.
* Bad, because reviewing documents is less natural for the Portuguese-speaking owner.

## Pros and Cons of the Options

### Everything in English

* Good, because it is ready for an international community.
* Good, because it keeps one language, consistent with Moonlight and LuminalShine.
* Bad, because reviewing documents is less natural for the Portuguese-speaking owner.

### Code and commits in English, internal docs in Portuguese

* Good, because the owner can review internal docs easily.
* Bad, because there are two languages.

### Commits and docs in Portuguese

* Good, because it matches the owner's language.
* Bad, because git history is mixed and contributors face a barrier.

### English plus bilingual docs

* Good, because it is accessible in two languages.
* Bad, because every document needs double maintenance.

## More Information

See [0019](0019-decision-and-change-records.md) (ADRs are written in English under this decision).
