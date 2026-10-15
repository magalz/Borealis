# UC-006: Send diagnostics from a TV to a phone

- **Status:** Accepted
- **Platform:** Android TV

## Summary

A user on a TV exports the diagnostic bundle and sends it to a phone on the
same local network, so that the file can be shared without a computer. The TV
opens one download link that works once and closes after 5 minutes.

## Actors

- Primary actor: the user, at the TV.
- Other actors and systems: a phone on the same local network, with a camera
  and a browser.

## Preconditions

- The TV and the phone are on the same private network.
- The user has opened Settings, then Privacy and diagnostics.

## Main flow

1. The user chooses Export diagnostic bundle. The system saves the bundle to
   Downloads as `borealis-diagnostics-<timestamp>.zip` and offers Send to
   phone.
2. The user chooses Send to phone. The dialog shows the file name and its size.
3. The system starts a one-download link on the TV's private IPv4 address. The
   link uses a one-time 128-bit token and expires after 5 minutes.
4. The dialog shows a QR code and the link text, for example
   `http://192.168.1.20:40125/<token>`, and a countdown.
5. The user scans the QR code with the phone's camera. The phone opens a small
   page on the TV's link, with the file name, the size and a Download link.
   Tapping Download fetches the zip at `/<token>/<file name>`. Opening the page
   does not use up the download (ADR 0051).
6. The system closes the link after the download. The dialog shows "Downloaded.
   The link is closed."

## Alternative flows

### A1: The link expires before the download (branches from step 4)

1. After 5 minutes the system closes the link. The dialog shows "The link
   expired" and the user can open Send to phone again for a new link.
2. The use case ends.

### A2: The user closes the dialog before the download (branches from step 4)

1. The user chooses Close. The system stops the link at once.
2. The use case ends.

### E1: A second download is requested (branches from step 6)

1. A request with the same token gets HTTP 410 Gone. The file is not sent again.
2. The use case ends.

### E2: No local network (branches from step 3)

1. The system finds no private IPv4 address. The dialog says that a phone
   cannot reach the TV and shows the adb command instead, for example
   `adb pull /sdcard/Download/borealis-diagnostics-<timestamp>.zip`.
2. No link is started. The use case ends.

### E3: The phone cannot reach the link (branches from step 5)

1. The download does not start. The link expires after 5 minutes, as in A1.
2. The use case ends.

## Postconditions

- On success: the phone has the bundle. No link is open on the TV.
- On expiry, close or a second request: no link is open on the TV.
- On no local network: no link is open; the user has the adb command.
- The TV keeps the bundle in Downloads. The next export replaces it, as the
  bundle store prunes its own earlier files.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-006-T1 | Given a TV with no private IPv4 address, when Send to phone opens, then the dialog shows the adb command and no link is started. | unit | exists (Sprint 3) |
| UC-006-T2 | Given a private IPv4 address, when the link starts, then the link uses only a private IPv4 address and a one-time 128-bit token. | unit | exists (Sprint 3) |
| UC-006-T3 | Given the file was downloaded once, when the same link is requested again, then the answer is HTTP 410. | unit | exists (Sprint 3) |
| UC-006-T4 | Given the link is open, when 5 minutes pass, then the link closes and the dialog shows that it expired. | unit | exists (Sprint 3) |
| UC-006-T5 | Given the dialog is closed before a download, when the user closes it, then the link stops and does not answer. | unit | exists (Sprint 3) |
| UC-006-T6 | Given a phone, when the diagnostics are exported, then Send to phone is not offered. | unit | exists (Sprint 3) |
| UC-006-T7 | Given the link is shown, when the QR code is read, then it encodes the link text. | unit | exists (Sprint 3) |

Planned tests cover the Phase 2 screens; tests marked as existing cover logic
that the Phase 2 screens reuse.

## Host dependency

None. The transfer runs on the TV and the phone. It needs no LuminalShine
capability.

## Related

- ADRs: [0051](../adr/0051-send-the-diagnostic-bundle-to-a-phone-with-a-qr-code.md),
  [0052](../adr/0052-structure-the-phase-2-ui.md).
- Policies: [privacy and logging](../policies/privacy-and-logging.md)
  ("Nothing leaves unless the user exports it or opts in to crash reporting.";
  the diagnostic bundle section).
- Existing logic: Send to phone on a TV exists from Sprint 3 (one download, 5
  minutes). The Phase 2 dialog is planned. The prototype's "Today" note for
  the screen says adb pull only.
- Development: [device validation](../development/device-validation.md)
  (export diagnostics on a TV).
- Existing tests (Sprint 3): `OneShotFileServerTest`, `TransferQrTest`,
  `LocalNetworkTest` and `ExportDiagnosticsPreferenceTest`.
- The link is plain `http://` on the local network, protected by the
  one-time token (ADR 0051). The transfer writes no audit entry: it moves
  an already redacted bundle that the user exported on purpose.
