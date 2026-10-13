---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0035 Store the REST token with Tink and Android Keystore

## Context and Problem Statement

The LuminalShine REST API uses a token that must be stored on the device. How should the token be protected at rest?

## Considered Options

* Tink + Android Keystore (AES-GCM with a non-exportable key)
* EncryptedSharedPreferences
* Raw Android Keystore
* Plaintext in DataStore (rejected: unacceptable)

## Decision Outcome

Chosen option: "Tink + Android Keystore (AES-GCM with a non-exportable key)", because the key is non-exportable and the encryption is done by a maintained library. The trade-off is a new dependency. Plaintext storage was rejected as unacceptable.

### Consequences

* Good, because the key is non-exportable and the library handles the encryption.
* Bad, because it adds a new dependency.

## Pros and Cons of the Options

### Tink + Android Keystore

* Good, because the AES-GCM key is non-exportable.
* Bad, because it adds a new dependency.

### EncryptedSharedPreferences

* Good, because it is simple.
* Bad, because it is deprecated.

### Raw Android Keystore

* Good, because it has no dependencies.
* Bad, because it is easy to get wrong.

### Plaintext in DataStore (rejected)

* Bad, because it is unacceptable for a token.

## More Information

See [0011](0011-datastore-room-and-keystore-for-new-persistence.md), [0031](0031-rest-client-okhttp.md) and [0021](0021-audit.md) (token use is audited).
