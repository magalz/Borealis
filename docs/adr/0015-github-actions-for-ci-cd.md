---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0015 Use GitHub Actions for CI/CD

## Context and Problem Statement

Which platform should run continuous integration and release pipelines? Moonlight and LuminalShine are hosted on GitHub.

## Considered Options

* GitHub Actions
* GitHub Actions with a self-hosted runner for real-device tests
* AppVeyor (upstream)
* GitLab CI

## Decision Outcome

Chosen option: "GitHub Actions", because it is integrated with PRs, releases and security features, has a large action ecosystem and a Gradle cache, and provides unlimited standard runners on public repositories (see [0024](0024-public-repository-from-the-start.md)).

### Consequences

* Good, because it is integrated with PRs, releases and security features.
* Good, because it has a large action ecosystem and a Gradle cache.
* Good, because public repositories get unlimited standard runners.
* Bad, because private repositories consume quota. This is moot, because the repository is public.

## Pros and Cons of the Options

### GitHub Actions

* Good, because it is integrated with PRs, releases and security.
* Good, because it has a large action ecosystem and a Gradle cache.
* Good, because public repositories get unlimited standard runners.
* Bad, because private repositories consume quota (moot, the repository is public).

### GitHub Actions with a self-hosted runner for real-device tests

* Good, because tests run on real phones, TVs, handhelds and even a real host.
* Bad, because the runner needs maintenance.
* Bad, because third-party PRs must never run on it.

### AppVeyor (upstream)

* Good, because upstream already has an `appveyor.yml`.
* Bad, because it has a small ecosystem and is separate from GitHub.

### GitLab CI

* Good, because it is a complete CI system.
* Bad, because it moves the project away from GitHub, where Moonlight and LuminalShine live.

## More Information

See [0016](0016-testing-strategy-and-tdd.md), [0022](0022-crash-reporting-and-privacy.md) (symbol upload by CI) and [0024](0024-public-repository-from-the-start.md).
