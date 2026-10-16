# UC-008: Add a host by address

- **Status:** Accepted
- **Platform:** All

## Summary

A user adds a LuminalShine host by its address when discovery does not find it,
for example across VLANs. The added host then appears on the Hosts screen and
can be paired ([UC-001](0001-pair-with-a-host.md)).

## Actors

- Primary actor: the user.
- Other actors and systems: the LuminalShine host, at the address the user types.

## Preconditions

- The host runs LuminalShine and is reachable from the device at its address.
- The user knows the address: an IP address, for example `192.168.1.20`, or a
  host name, for example `desk-pc.local`.

## Main flow

1. The user chooses Add host on the Hosts screen (on a phone, the + button).
2. The system shows the Add host screen with one Address field. The system
   keyboard opens at once on a phone. On a TV the keyboard opens on OK.
3. The user types the address. The port is needed only when the LuminalShine
   port was changed from the default 47989.
4. The user chooses Add host.
5. The system adds the host to the Hosts screen and shows the message "`<name>`
   added. Pair it to see its apps."
6. The user pairs the host ([UC-001](0001-pair-with-a-host.md)).

## Alternative flows

### A1: The user cancels (branches from step 3)

1. The user chooses Cancel. The Hosts screen returns and nothing is added.
2. The use case ends.

### A2: The user leaves the address field with the D-pad (TV, branches from step 3)

1. The field keeps the D-pad for its caret. Up and Down move the focus to the
   item above or below. Down goes to the Add host button.
2. Left moves the caret while the field has text. When the caret is at the start,
   or the field is empty, Left goes to the rail. The rail is entered on its
   selected item, which is Add host on this screen. The typed text is kept.
3. The user goes back to the field, or continues on the rail. The flow rejoins
   the main flow at step 3 when the user returns to the field.

### E1: The address fails the 12.2 checks (branches from step 4)

1. The rules are the ones Moonlight 12.2 applies. An empty field shows "You
   must enter an IP address". A name that does not resolve shows "Unable to
   resolve PC address". A private (site-local) IPv4 address that is not on any
   of the device's own subnets fires the 12.2 subnet check
   (`AddComputerManually.isWrongSubnetSiteLocalAddress`) and shows
   `addpc_wrong_sitelocal` ("That address doesn't look right...").
2. The host is not added.
3. The use case ends.

### E2: The host does not answer (branches from step 4)

1. The host is not added, as in Moonlight 12.2 only. A dialog shows "Unable to
   connect to the specified computer. Make sure the required ports are allowed
   through the firewall."
2. The use case ends.

## Postconditions

- On success: the host is listed on the Hosts screen with its address. It is
  not paired yet, so its action is Pair.
- On failure or cancel: the list is unchanged. No secret is stored.
- Forget removes the host from this device, whatever the host answers. It first
  sends a best-effort `/unpair`. LuminalShine has no such route and answers 404,
  so the PC may keep trusting this device. The Forget text says so, and asks the
  user to remove the device in the LuminalShine Web UI (Clients page).

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-008-T1 | Given the Add host screen is opened, when it appears, then the Address field has focus and the keyboard opens. | UI | exists (Sprint 4, UI) |
| UC-008-T2 | Given the address `192.168.1.20`, when the user chooses Add host, then the host appears on the Hosts screen with Pair as its action. | unit | planned |
| UC-008-T3 | Given an address without a port, when the host is added, then the default port 47989 is used. | unit | exists (Sprint 4, unit) |
| UC-008-T4 | Given an address with a port, when the host is added, then that port is used. | unit | exists (Sprint 4, unit) |
| UC-008-T5 | Given the user chooses Cancel, when the screen closes, then the Hosts list is unchanged. | UI | exists (Sprint 4, UI) |
| UC-008-T6 | Given the address field has text on a TV, when the user presses Down, then the focus moves to Add host; when the caret is at the start, Left goes to the rail and the text is kept. | UI | exists (Sprint 5, UI; device round) |
| UC-008-T7 | Given a host is added by address, when the user chooses Forget, then the text says that the PC may keep trusting this device until it is removed in the LuminalShine Web UI. | UI | exists (Sprint 5, UI; device round) |

Planned tests cover the Phase 2 screens; tests marked as existing cover logic
that the Phase 2 screens reuse.

## Host dependency

The client connects to the GameStream HTTP port (47989 by default) to check the
host ([host contract, section 2](../host-contract/luminalshine.md)). Manual add
is the only way to reach a host that discovery does not find.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md).
- Host contract sections: [1](../host-contract/luminalshine.md#1-host-identity),
  [2](../host-contract/luminalshine.md#2-ports-and-discovery).
- Policies: [privacy and logging](../policies/privacy-and-logging.md) (host
  addresses are masked in logs when "Mask addresses in logs and diagnostics"
  is on).
- Existing logic: the manual add of Moonlight 12.2 (`AddComputerManually`),
  unchanged in Sprints 1 to 3. The Phase 2 screen is planned.
