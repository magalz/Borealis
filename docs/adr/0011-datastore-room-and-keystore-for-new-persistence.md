---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0011 Use DataStore, Room and Android Keystore for new persistence

## Context and Problem Statement

Upstream keeps SharedPreferences (stream preferences read by `Game.java`) and its SQLite host database. New settings screens write the upstream keys where upstream code consumes them. Which storage mechanisms should new code use for settings, session history and audit data, and for secrets?

## Considered Options

* DataStore (new settings) + Room (session history, audit trail) + secrets in Android Keystore
* DataStore + rotating JSONL files
* DataStore only
* Room + DataStore, also migrating upstream storage
* Earlier round: SharedPreferences/SQLite only
* Earlier round: SQLDelight

## Decision Outcome

Chosen option: "DataStore (new settings) + Room (session history, audit trail) + secrets in Android Keystore", because each data shape gets the right tool: queryable history with migrations and compile-time-checked queries from Room.

### Consequences

* Good, because each data shape uses the right tool.
* Good, because history is queryable, with formal migrations and compile-time-checked queries.
* Bad, because three mechanisms are in use once upstream's are counted.
* Bad, because Room adds KSP to the build.

## Pros and Cons of the Options

### DataStore + Room + Keystore

* Good, because each data shape gets the right tool.
* Good, because history is queryable with migrations and compile-time-checked queries.
* Bad, because three mechanisms are in use once upstream's are counted.
* Bad, because Room adds KSP.

### DataStore + rotating JSONL files

* Good, because it is simple and export-friendly, and needs no KSP.
* Bad, because queries are limited.
* Bad, because there are no formal schema migrations.

### DataStore only

* Good, because it is a single new mechanism.
* Bad, because it rewrites the whole file on each event, which is bad for growing data.
* Bad, because it offers no queries.

### Room + DataStore, also migrating upstream storage

* Good, because there is a single model.
* Bad, because it diverges in central upstream code and requires user data migration.

### SharedPreferences/SQLite only (earlier round)

* Good, because it needs no new dependencies.
* Bad, because it is synchronous and uses raw SQL.

### SQLDelight (earlier round)

* Good, because it has a multiplatform advantage.
* Bad, because that advantage is irrelevant after [0003](0003-native-apps-per-platform-with-shared-core.md).

## More Information

See [0021](0021-audit.md) (the audit trail is stored in Room) and [0035](0035-secrets-storage-with-tink-and-keystore.md).

Amended by [0050](0050-diagnostics-audit-and-interim-settings.md).
