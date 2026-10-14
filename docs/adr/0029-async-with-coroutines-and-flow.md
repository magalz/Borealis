---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0029 Use Kotlin Coroutines and Flow for async

## Context and Problem Statement

Which asynchronous mechanism should new Kotlin code use for background work and state streams?

## Considered Options

* Kotlin Coroutines + Flow
* RxJava 3
* Executors + callbacks as upstream
* LiveData + Executors

## Decision Outcome

Chosen option: "Kotlin Coroutines + Flow", because it is native to Kotlin and Compose. The trade-off is a learning curve for structuring code.

### Consequences

* Good, because it is native to Kotlin and Compose.
* Bad, because the team must learn how to structure coroutine code.

## Pros and Cons of the Options

### Kotlin Coroutines + Flow

* Good, because it is native to Kotlin and Compose.
* Bad, because there is a learning curve for structuring code.

### RxJava 3

* Good, because it is powerful.
* Bad, because it is heavy and declining.

### Executors + callbacks as upstream

* Good, because it has zero dependencies.
* Bad, because it leads to callback hell.

### LiveData + Executors

* Good, because it is simple.
* Bad, because it is legacy and fits Compose poorly.

## More Information

See [0007](0007-official-android-app-architecture.md) (ViewModel and StateFlow) and [0031](0031-rest-client-okhttp.md).
