# Test fixtures

Host responses and golden vectors shared by the borealis-core tests and the Android tests. The Android modules read this directory through the `borealis.fixtures.dir` test system property.

## Layout

| Path | Content |
| --- | --- |
| `luminalshine/<host version>/` | Responses of one LuminalShine release, named after the endpoint (`serverinfo-http.xml` is `/serverinfo` over HTTP). |
| `vectors/*.tsv` | Golden input/output vectors for the core host rules, read by the GoogleTest suite and by the JNI instrumented test. |

Each host version folder has a `PROVENANCE.md` with one row per file.

## Provenance

- `device`: captured from a running host.
- `source`: written by hand from the host source code, with the file and lines it follows.

## Scrubbing

The repository is public. Before a capture is committed, replace:

| Field | Replacement |
| --- | --- |
| Host name | `LUMINALSHINE-HOST` |
| `uniqueid` | `00000000-0000-0000-0000-000000000001` |
| MAC address | `00:00:00:00:00:00` |
| IPv4 address | an address in `192.0.2.0/24` |
| IPv6 address | an address in `2001:db8::/32` |

Never commit certificates, keys, pairing secrets, `rikey` values or session URLs.
