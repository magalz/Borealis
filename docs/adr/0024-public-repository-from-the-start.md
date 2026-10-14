---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0024 Make the repository public from the start

## Context and Problem Statement

GPL-3.0 requires offering the source to recipients of the software. When should the repository become public, and should it be public from the start?

## Considered Options

* Public from the start
* Private until the first release
* Private, with a public mirror per release
* Always private

## Decision Outcome

Chosen option: "Public from the start", because it gives unlimited GitHub Actions, transparency, and lets the community follow the work and open issues and PRs early.

### Consequences

* Good, because Actions runners are unlimited, and the community can follow the work.
* Good, because external issues and PRs can arrive early.
* Bad, because work in progress is exposed.

## Pros and Cons of the Options

### Public from the start

* Good, because Actions runners are unlimited.
* Good, because the project is transparent and the community can follow it.
* Good, because external issues and PRs arrive early.
* Bad, because work in progress is exposed.

### Private until the first release

* Good, because it gives freedom to experiment.
* Bad, because it is subject to the Actions quota.
* Bad, because the history becomes public later.

### Private, with a public mirror per release

* Good, because it complies with GPL for each version.
* Bad, because mirroring automation is needed.
* Bad, because there is no day-to-day community.

### Always private

* Good, because it gives maximum control.
* Bad, because GPL compliance becomes bureaucratic.
* Bad, because it creates friction with the stores.

## More Information

See [0015](0015-github-actions-for-ci-cd.md) and [0002](0002-base-the-app-on-vanilla-moonlight-12-2.md).
