---
status: accepted
date: 2026-10-15
decision-makers: João Pedro Magalhães
---

# 0051 Send the diagnostic bundle from a TV to a phone with a QR code

## Context and Problem Statement

[0050](0050-diagnostics-audit-and-interim-settings.md) saves the diagnostic bundle to `Download/` on a TV, because a TV has no share target and no browser. The only way to fetch it is `adb pull`, which most testers cannot run on a Google TV. How can a TV user get the bundle (30 to 60 KB; the writer caps it at 16 MiB) onto a phone without `adb`, an account or an upload?

## Decision Drivers

* No new network destination beyond the local network, and nothing uploaded
* The bundle is already redacted; the transfer must not add content
* Few dependencies, and the licences stay compatible with GPL-3.0-or-later
* Works on a TV with a remote only (D-pad), and a phone needs nothing but its camera and browser
* Small, testable code with a bounded attack surface

## Considered Options

The owner chose among these alternatives on 2026-10-15.

* Own one-shot HTTP server and ZXing: a plain `ServerSocket` serves the zip once behind a random token, and ZXing core draws the QR code of the link
* NanoHTTPD and ZXing: the same flow with NanoHTTPD as the HTTP server
* A QR code with only a summary: encode a few hundred bytes (app version, device, last errors) and no file
* Defer to the Phase 2 UI: leave `adb pull` until the new settings screen has a proper flow

## Decision Outcome

Chosen option: "Own one-shot HTTP server and ZXing", because the flow needs one route that answers once, which is less code than a server dependency's surface, and the full bundle reaches the phone.

Decision details:

1. **Flow.** On a TV the saved dialog's neutral button is "Send to phone" (on a phone it stays "Share…"). It starts the server, shows a QR code of the link, the link as selectable text, a countdown and a status (Waiting, Downloaded, Expired). The phone scans it with its camera and downloads the zip in its browser.
2. **Server.** A plain `java.net.ServerSocket`; ZXing core 3.5.4 (Apache-2.0) is the only new dependency.
   * It binds only the active network's private IPv4 address (Wi-Fi or Ethernet, site-local, from `ConnectivityManager`), never `0.0.0.0`, on an ephemeral port. With no such network the dialog says so and the `adb pull` text stays reachable.
   * The token is 128 bits from `SecureRandom`, in hex, compared in constant time.
   * Routes: `GET /<token>` is a static page with the file name and size and a download link (link-preview fetchers may request it, so it does not use up the download); `GET /<token>/<file name>` is the zip (`application/zip`, `Content-Length`, `Content-Disposition: attachment`, `Cache-Control: no-store`, `Connection: close`). After the first complete zip response the routes answer 410 and the server stops. A wrong token or path is 404, other methods 405, a malformed request 400, a silent client 408, an oversized head 431.
   * Limits: one connection at a time, a 10 second read timeout (the whole request head must arrive within it), a request head of at most 8 KiB, and no request body is read.
   * Lifetime: it stops at the first of a complete download, 5 minutes and `stop()`. The dialog calls `stop()` when it is dismissed or when the settings activity is finishing or destroyed. A watchdog thread enforces the 5 minutes: at the deadline it ends the transfer and closes the listening socket and the connection being served, so a client that stalls mid-download or sends nothing cannot keep the link alive. `stop()` is idempotent, closes the same sockets, and waits for both threads.
   * The log records the start (port and expiry), downloads (status only) and the stop. It never records the token or the URL.
3. **QR code.** ZXing `QRCodeWriter`, error correction M, a 2-module margin. The `BitMatrix` is pure JVM, so a test decodes it back to the exact URL; the bitmap is black on white, so it reads inside a dark dialog, and about 60% of the shorter screen side.
4. **Content.** The file served is the bundle that `BundleBuilder` already built and redacted. Nothing is added.

### Consequences

* Good, because a TV tester gets the whole bundle on a phone with no `adb`, account or upload.
* Good, because the server is small (one route that answers once, no framework) and covered by tests that decode the QR code and exercise each status code, the limits and `stop()`.
* Good, because the only new dependency is ZXing core, which is Apache-2.0 and already declared in `NOTICE`.
* Bad, because the transfer is plain HTTP: a device on the same network that sees the link can download the file before the phone does. The token is one-time and random, the window is 5 minutes and the file is redacted, but the user must use a network they trust. [PRIVACY.md](../../PRIVACY.md) says so.
* Bad, because the app has a network listener for the first time, if only while the dialog is open. It is bound to the private address, one connection at a time, with small limits, and the policy lists them.
* Bad, because the server needs a second thread, the watchdog: writes have no timeout in `java.net`, so without it a client that stops reading would hold the serving thread past the 5 minutes.

## Pros and Cons of the Options

### Own one-shot HTTP server and ZXing (chosen)

* Good, because it adds only ZXing and keeps the server small and fully tested.
* Good, because the app controls every response header and limit.
* Bad, because the app owns a small HTTP parser (request line and headers only) that a library would otherwise own.

### NanoHTTPD and ZXing

* Good, because the HTTP parsing is an established library.
* Bad, because it adds a dependency, a licence entry and a general-purpose server for one route, with default behaviour (keep-alive, threads per connection, upload handling) to turn off.

### A QR code with only a summary

* Good, because it needs no server and no network.
* Bad, because a QR code holds a few hundred bytes at most, far below the 30 to 60 KB bundle, so the logs and decoder report that make a bug report useful are lost.

### Defer to the Phase 2 UI

* Good, because no code is written now.
* Bad, because Sprint 3 testers on a Google TV keep needing `adb pull`, and the Phase 2 UI does not remove the need for a transfer path.

## More Information

* Related: [0050](0050-diagnostics-audit-and-interim-settings.md), [0020](0020-logging.md) and [0022](0022-crash-reporting-and-privacy.md).
* Policy: [privacy-and-logging.md](../policies/privacy-and-logging.md). User statement: [PRIVACY.md](../../PRIVACY.md).
