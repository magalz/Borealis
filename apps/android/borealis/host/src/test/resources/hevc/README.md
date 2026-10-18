# HEVC SPS fixtures

Raw HEVC sequence parameter set NAL units (type 33), each with its Annex-B start code
(`00 00 00 01`), as emitted by x265. They are inputs of `HevcSpsTest`; `HevcSpsMonitorTest` builds its
SPS with `HevcSpsBuilder` and does not read them. The files are marked `binary` in `.gitattributes`.
They are synthetic test-pattern streams: no captured or personal data.

Tool versions: ffmpeg 8.1.1 (gyan.dev full build, gcc 15.2.0) with libx265 (x265 4.2+3-3f4120d,
8bit+10bit+12bit).

## Generation

`gen NAME PIX_FMT X265_PARAMS [SIZE]` encodes one `testsrc` frame (1920x1080 unless SIZE is given)
and keeps only the SPS NAL:

```sh
gen() {
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc=size=${4:-1920x1080}:rate=1 -frames:v 1 \
    -c:v libx265 -pix_fmt "$2" -x265-params "log-level=error$3" \
    -bsf:v filter_units=pass_types=33 -f hevc "$1"
}
gen main-420-8bit.bin yuv420p ""
gen main10-420-10bit.bin yuv420p10le ""
gen rext-444-8bit.bin yuv444p ""
gen rext-444-10bit.bin yuv444p10le ""
gen main-420-8bit-3-temporal-layers.bin yuv420p ":temporal-layers=3"
# 1918x1078 is not a multiple of the 8-pixel minimum CU: x265 codes 1920x1080 and sets a
# conformance window (ffprobe: width,height 1918,1078; coded_width,coded_height 1920,1080).
gen main-420-8bit-conformance-window.bin yuv420p "" 1918x1078
```

## Expected content

| File | general_profile_idc | chroma_format_idc | bit depth luma/chroma | sps_max_sub_layers_minus1 |
| --- | --- | --- | --- | --- |
| `main-420-8bit.bin` | 1 (Main) | 1 (4:2:0) | 8/8 | 0 |
| `main10-420-10bit.bin` | 2 (Main 10) | 1 (4:2:0) | 10/10 | 0 |
| `rext-444-8bit.bin` | 4 (RExt) | 3 (4:4:4) | 8/8 | 0 |
| `rext-444-10bit.bin` | 4 (RExt) | 3 (4:4:4) | 10/10 | 0 |
| `main-420-8bit-3-temporal-layers.bin` | 1 (Main) | 1 (4:2:0) | 8/8 | 2 |
| `main-420-8bit-conformance-window.bin` | 1 (Main) | 1 (4:2:0) | 8/8 (`conformance_window_flag` is 1) | 0 |

All six SPS contain emulation-prevention bytes (`00 00 03`).
