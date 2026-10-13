---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0031 Use OkHttp with kotlinx.serialization for the LuminalShine REST client

## Context and Problem Statement

Which HTTP client should call the LuminalShine REST API? The client must reuse the TLS and pinning behaviour that upstream's NvHTTP already uses.

## Considered Options

* OkHttp directly + kotlinx.serialization
* Retrofit
* Ktor client
* HttpURLConnection

## Decision Outcome

Chosen option: "OkHttp directly + kotlinx.serialization", because OkHttp is already a dependency, the same TLS and pinning as NvHTTP is reused, and kotlinx.serialization is already in use (see [0030](0030-serialization-with-kotlinx.md)). The trade-off is a bit more code.

### Consequences

* Good, because OkHttp is already a dependency and NvHTTP's TLS and pinning are reused.
* Bad, because a bit more code is needed than with a declarative client.

## Pros and Cons of the Options

### OkHttp directly + kotlinx.serialization

* Good, because it is already a dependency.
* Good, because it reuses NvHTTP's TLS and pinning.
* Bad, because it needs a bit more code.

### Retrofit

* Good, because it is declarative.
* Bad, because it adds an extra layer for a few endpoints.

### Ktor client

* Good, because it is idiomatic Kotlin.
* Bad, because it brings another TLS stack.

### HttpURLConnection

* Good, because it has zero dependencies.
* Bad, because it is verbose.

## More Information

See [0029](0029-async-with-coroutines-and-flow.md), [0030](0030-serialization-with-kotlinx.md) and [0035](0035-secrets-storage-with-tink-and-keystore.md) (REST token storage).
