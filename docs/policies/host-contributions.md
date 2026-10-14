# Host contributions policy

Borealis is tailored to the
[LuminalShine](https://github.com/NortheBridge/luminalshine) host (a Windows
fork of Sunshine via Vibeshine). Borealis does not control the host. This page
says how to propose host changes and how the client must behave without them.

## Principle: the client must work without host changes

- Borealis must remain usable with any compatible host, including stock
  Sunshine and older LuminalShine versions, using the behaviour Moonlight
  already relies on.
- Features that depend on a LuminalShine capability are detected at runtime
  (version, capability or endpoint probing) and **degrade gracefully** when
  the capability is absent: hide or disable the feature, explain why, and
  never fail the basic stream.
- Do not assume host behaviour that is not documented in the host contract
  (see the host-contract section of the [docs index](../README.md)) or verified against the host code.

## Proposing a host change

Host changes are proposals to LuminalShine, written in English.

1. **Check first.** Search LuminalShine's issues and code to confirm the
   capability does not exist.
2. **Write the need.** Describe the client use case (reference the UC-NNN in
   [../use-cases/](../use-cases/README.md)), what the client cannot do today,
   and the smallest host change that would fix it.
3. **Open an issue** in the LuminalShine repository with the proposal: API
   shape, request and response examples, compatibility behaviour for older
   clients, and security considerations. Link the Borealis use case or ADR.
4. **Offer a PR** to LuminalShine if the owner of that project is open to it.
   It follows LuminalShine's own contribution rules, not Borealis's.
5. **Track it.** Record the proposal and its status in the host contract notes
   in this repository. Do not ship client code that requires an unmerged host
   change; develop the client side behind capability detection.
6. **Contract tests.** Document the expected host behaviour and add
   golden-fixture contract tests on the Borealis side so that regressions in
   either project are visible.

## Security and privacy

- Do not paste real PINs, keys, certificates or tokens into host issues. Use
  redacted or throwaway examples.
- Host vulnerabilities go to LuminalShine's security channel, not to public
  issues. See [SECURITY.md](../../SECURITY.md).

## Compatibility records

Each Borealis release records the LuminalShine versions it was tested with
(see [release.md](release.md)).
