# LuminalShine 26.09.1 fixtures

Host: LuminalShine 26.09.1 (Windows, default configuration: PyroWave off).

| File | Provenance | Capture | Scrubbed fields |
| --- | --- | --- | --- |
| `serverinfo-http.xml` | `device` | 2026-10-14, unpaired client, `GET http://localhost:47989/serverinfo?uniqueid=0123456789ABCDEF&uuid=00000000-0000-0000-0000-000000000000` | `hostname`, `uniqueid`, `LocalIP` (was the loopback address); `mac` was already the placeholder |
| `unpair-http.response` | `device` | 2026-10-14, raw bytes read from a keep-alive socket after `GET /unpair?uniqueid=0123456789ABCDEF&uuid=00000000-0000-0000-0000-000000000000` over plain HTTP; the host closed the socket about 80 ms later | none needed (no host identifiers in the response) |

## Notes

- `serverinfo-http.xml`: `appversion` is `7.1.431.-1` (the Sunshine-style version: nothing in it identifies LuminalShine) and `ServerCodecModeSupport` is `1835777` (`0x001C0301`: H.264, HEVC, HEVC Main10, H.264 High 4:4:4, HEVC RExt 4:4:4 8 and 10 bit; no AV1, no PyroWave bits).
- `unpair-http.response` is byte-exact (`binary` in `.gitattributes`): `HTTP/1.1 200 OK` with `Content-Length: 64` and an XML body with `status_code="404"`, followed by 88 more bytes on the same connection (`HTTP/1.1 404 NOT FOUND`, CRLF, and the same XML again, with no header block), then the host closes the socket.
- Over plain HTTP the host always reports `PairStatus` `0` and the placeholder MAC `00:00:00:00:00:00` (`luminalshine/src/nvhttp.cpp:2494-2523`); the PyroWave bits appear only when PyroWave is enabled and available (`:2573-2575`).
