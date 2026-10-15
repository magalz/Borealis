# UC-007: Review and erase the audit trail

- **Status:** Accepted
- **Platform:** All

## Summary

A user reads the local audit trail of security-relevant events, in plain words
with the event code, and can erase all of it. The trail stays on the device.

## Actors

- Primary actor: the user.
- Other actors and systems: none. The trail is local.

## Preconditions

- The app is installed. The audit trail may hold no entries yet.

## Main flow

1. The user opens Settings, then Privacy and diagnostics, then Audit trail.
2. The system lists the entries newest first. Each entry shows its time, a
   title in plain words, the event code and a short detail.
3. The user scrolls the list. On touch, the user taps an entry to see its
   detail. On a TV, the D-pad scrolls entry by entry.
4. The user chooses Erase audit trail. Today the row reads "Delete the local
   record of pairing, launch and session events". The Phase 2 design adds the
   text "Cannot be undone" to the row (planned).
5. (planned) The system asks "Erase the audit trail?" and explains that exported
   bundles are not changed. Cancel has the focus.
6. (planned) The user chooses Erase. The system erases the entries and shows
   "Audit trail erased".

## Alternative flows

### A1: The trail is empty (branches from step 2)

1. The screen says "No events recorded yet." The use case ends.

### A2: Entries leave the trail by retention (branches from step 2)

1. The system keeps the newest 2,000 entries or 90 days, whichever is smaller.
   Older entries are removed without a user action.
2. The list shows only the entries that remain.

### E1: The erase fails (branches from step 6)

1. The entries that were not erased stay in the list. The system shows an error.
2. (planned) The message is "Could not erase the audit trail". No audit entry is
   written.
3. The use case ends.

## Postconditions

- On success: no audit entries remain on the device.
- On failure: the entries are unchanged.
- The trail is in local storage and is excluded from Android backup. It is never
  uploaded ([privacy and logging](../policies/privacy-and-logging.md#audit-trail)).

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-007-T1 | Given entries exist, when the screen opens, then they are listed newest first, each with its event code. | UI | planned |
| UC-007-T2 | Given a CERTIFICATE_CHANGED entry, when the list is shown, then its title uses the warning text colour. | UI | planned |
| UC-007-T3 | Given the user confirms Erase, when the erase ends, then no entries remain and "Audit trail erased" is shown. | unit | exists (Sprint 2, unit) |
| UC-007-T4 | Given entries older than 90 days or beyond 2,000 entries, when retention runs, then the oldest entries are removed. | unit | exists (Sprint 2, unit) |
| UC-007-T5 | Given the same host certificate change or TLS failure within 24 hours, when it is recorded, then it is stored once. | unit | exists (Sprint 2, unit) |
| UC-007-T6 | Given "Mask addresses in logs and diagnostics" is on, when the trail is shown or exported, then no full address appears in the host field. | unit | planned |

Planned tests cover the Phase 2 screens; tests marked as existing cover logic
that the Phase 2 screens reuse.

## Host dependency

None. The trail is local. REST and token events arrive with Phase 3
([privacy and logging](../policies/privacy-and-logging.md#audit-trail)).

## Related

- ADRs: [0021](../adr/0021-audit.md) (audit trail), [0050](../adr/0050-diagnostics-audit-and-interim-settings.md)
  (retention and the export), [0052](../adr/0052-structure-the-phase-2-ui.md).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing logic: the audit trail is kept, exported in the diagnostic bundle and
  can be erased from the Sprint 2 and 3 interim screens. The in-app viewer is
  planned for Phase 2.
- Existing tests: `AuditStoreTest`, `AuditDeduplicatorTest`,
  `EraseAuditPreferenceTest`.
