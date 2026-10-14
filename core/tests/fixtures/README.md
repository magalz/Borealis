# Test fixtures

Host responses and golden vectors shared by the borealis-core tests and the Android tests. The Android modules read this directory through the `borealis.fixtures.dir` test system property.

## Layout

| Path | Content |
| --- | --- |
| `luminalshine/<host version>/` | Responses of one LuminalShine release, named after the endpoint (`serverinfo-http.xml` is `/serverinfo` over HTTP). |
| `vectors/*.tsv` | Golden input/output vectors for the core host rules, read by the GoogleTest suite and, once the Android JNI instrumented test exists (task T4), by that test. |
| `redaction/secret-canaries.tsv` | Synthetic secrets and the text the log redactor must produce for them, read by the redaction tests of `:borealis:logging` and `:borealis:diagnostics`. |

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

## Golden vectors

`vectors/*.tsv` pin the behaviour of the three core host functions. The GoogleTest suite (`tests/vectors_test.cpp`) replays them through the C ABI today. The Android JNI instrumented test (task T4) will replay the same files through the Kotlin binding, so a rule cannot drift between platforms.

Common format:

- UTF-8, LF or CRLF line ends, one record per line, fields separated by a single TAB. Fields are never trimmed or quoted: spaces at either end of a field are data, and there is no escape syntax (a field cannot contain a TAB or a line break).
- Blank lines and lines starting with `#` are ignored.
- The first remaining line is the header; it names the columns and is skipped by the readers.
- Integers are decimal, or hexadecimal with a `0x` prefix. Names (`LAUNCH`, `SUNSHINE`, ...) are the `BOREALIS_*` suffixes in `core/include/borealis/host.h`.
- A string input is a literal field, except `<null>` (a NULL pointer with length 0) and `<empty>` (a non-NULL pointer with length 0).

| File | Columns | Function |
| --- | --- | --- |
| `host_caps.tsv` | `appversion`, `codec_mask`, `family` (`UNKNOWN`, `GFE`, `SUNSHINE`), `v0`..`v3`, `codec_flags`, `pyrowave_profiles` | `borealis_host_parse_caps`; every row returns `BOREALIS_OK` |
| `launch_decide.tsv` | `verb` (`LAUNCH`, `RESUME`), `status`, `message`, `attempts` (retries already made in the category), `action` (`FAIL`, `RETRY`, `FALLBACK_TO_LAUNCH`), `delay_ms`, `category` (`PERMANENT`, `PENDING`, `RESTORING_DISPLAY`, `GPU_NOT_READY`, `CAPTURE_OR_DISPLAY`, `NO_RUNNING_APP`), `max_attempts` | `borealis_launch_decide`; every row returns `BOREALIS_OK` |
| `launch_fps.tsv` | `is_gfe` (1 or 0), `launch_fps`, `stream_fps`, `expected` | `borealis_launch_fps` |

Notes for replayers that are not C: `codec_mask` values above `0x7FFFFFFF` must be parsed as unsigned (a `Long` in Java and Kotlin) and narrowed to a 32-bit int, and `attempts` is `uint32_t` in the ABI, so a negative 32-bit value from Java is a huge count and yields `FAIL`. A message field cannot contain a TAB or a line break, and a trailing CR is stripped as part of the line end.

Error returns (NULL arguments, over-long inputs, unknown verbs) cannot be written as text and are covered by the unit tests only.

## Redaction canaries

`redaction/secret-canaries.tsv` uses the TSV rules above (header row, `#` comments, no quoting). Columns: `id`, `input`, `expected` and `secret`. `expected` is what the default redactor (address masking off) writes for `input`; `secret` is a value that must not appear in the output with masking on or off. Every value is made up: add a row for each new kind of secret that a log line could carry, and never a real credential.
