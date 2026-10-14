# LuminalShine 26.09.1 fixtures

Host: LuminalShine 26.09.1 (Windows, default configuration: PyroWave off).

| File | Provenance | Capture | Scrubbed fields |
| --- | --- | --- | --- |
| `serverinfo-http.xml` | `device` | 2026-10-14, unpaired client, `GET http://localhost:47989/serverinfo?uniqueid=0123456789ABCDEF&uuid=00000000-0000-0000-0000-000000000000` | `hostname`, `uniqueid`, `LocalIP` (was the loopback address); `mac` was already the placeholder |

## Notes

- `serverinfo-http.xml`: `appversion` is `7.1.431.-1` (the Sunshine-style version: nothing in it identifies LuminalShine) and `ServerCodecModeSupport` is `1835777` (`0x001C0301`: H.264, HEVC, HEVC Main10, H.264 High 4:4:4, HEVC RExt 4:4:4 8 and 10 bit; no AV1, no PyroWave bits).
- Over plain HTTP the host always reports `PairStatus` `0` and the placeholder MAC `00:00:00:00:00:00` (`luminalshine/src/nvhttp.cpp:2494-2523`); the PyroWave bits appear only when PyroWave is enabled and available (`:2573-2575`).
