# Use cases

A use case describes one thing a person does with Borealis and what must be
true afterwards. Use cases are the source of acceptance tests: each use case
lists the tests that prove it, and those tests are written first (see
[../development/tdd.md](../development/tdd.md)).

## Numbering and files

- Identifier: `UC-NNN`, three digits, assigned in order and never reused (for
  example `UC-001`).
- File name: `NNNN-short-title.md` with a four-digit prefix and a lowercase,
  hyphenated title, for example `0001-pair-with-host.md`. The file `0000-template.md` is
  the template and is not a use case.
- The H1 of the file is `UC-NNN: Title`.
- A withdrawn use case keeps its number and is marked `Status: Withdrawn`.

## How to write one

1. Copy [0000-template.md](0000-template.md).
2. Name the **actor** and the **goal** in user terms, not in implementation
   terms.
3. List **preconditions** that must hold before the flow starts.
4. Write the **main flow** as numbered steps that alternate between actor
   actions and system responses.
5. Write **alternative flows** for failures and variations (host offline,
   certificate changed, capability missing on an older host), referencing the
   main-flow step they branch from.
6. State **postconditions**: what is true on success, and what is guaranteed
   on failure (for example no secrets stored, an audit entry written).
7. Write **acceptance tests** as observable behaviours. Each should be
   expressible as an automated test (unit, Robolectric/Compose, emulator,
   contract). Mark which ones exist.
8. Link **related ADRs** and **host contract sections**. If the use case needs
   a host feature, note how the client behaves without it (see
   [../policies/host-contributions.md](../policies/host-contributions.md)).

## Conventions

- Keep one goal per use case; split large flows.
- Do not invent features: a use case describes behaviour that is decided or
  proposed. Set `Status` to `Draft`, `Accepted`, `Implemented` or `Withdrawn`.
- Mention logging and audit-trail entries when the flow is security-relevant
  ([../policies/privacy-and-logging.md](../policies/privacy-and-logging.md)).
- Use throwaway example values for PINs, addresses and certificates.

## Index

| ID | Title | Status |
| --- | --- | --- |
| UC-001 | [Pair with a host](0001-pair-with-a-host.md) | Accepted |
| UC-002 | [Start a stream](0002-start-a-stream.md) | Accepted |
| UC-003 | [Join or replace a running app](0003-join-or-replace-a-running-app.md) | Accepted |
| UC-004 | [Use the in-game menu](0004-use-the-in-game-menu.md) | Accepted |
| UC-005 | [Type with the on-screen keyboard](0005-type-with-the-on-screen-keyboard.md) | Accepted |
| UC-006 | [Send diagnostics from a TV to a phone](0006-send-diagnostics-from-a-tv-to-a-phone.md) | Accepted |
| UC-007 | [Review and erase the audit trail](0007-review-and-erase-the-audit-trail.md) | Accepted |
| UC-008 | [Add a host by address](0008-add-a-host-by-address.md) | Accepted |
