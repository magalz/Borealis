# UC-NNN: Title

- **Status:** Draft | Accepted | Implemented | Withdrawn
- **Platform:** Android (phone/tablet) | Android TV | Handheld | All

## Summary

One or two sentences: who does what, and why it matters.

## Actors

- Primary actor: who starts it.
- Other actors and systems: for example the LuminalShine host, the Android
  system.

## Preconditions

- What must be true before the flow starts.

## Main flow

1. The actor does something.
2. The system responds.
3. ...

## Alternative flows

### A1: Name of the variation (branches from step N)

1. ...
2. Rejoins the main flow at step M, or ends the use case.

### E1: Name of a failure (branches from step N)

1. What the user sees and what the system does.
2. Which log or audit entry is written.

## Postconditions

- On success: what is true afterwards.
- On failure: what is guaranteed (for example no secret stored, state
  unchanged).

## Acceptance tests

Each item is an observable behaviour that can be automated. Write these tests
first.

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-NNN-T1 | Given ..., when ..., then ... | unit / UI / emulator / contract | planned |

## Host dependency

State whether the use case needs a LuminalShine capability and how the client
behaves without it.

## Related

- ADRs: link to entries in [../adr/README.md](../adr/README.md).
- Host contract sections: link to the relevant `docs/host-contract/` pages.
- Policies: for example [privacy and logging](../policies/privacy-and-logging.md).
