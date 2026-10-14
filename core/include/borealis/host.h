/*
 * borealis-core LuminalShine host rules: capability parsing, the launch/resume
 * retry decision and the /launch frame rate.
 *
 * Everything here is pure and stateless: no I/O, no global mutable state, no
 * allocation, thread-safe. Callers own every buffer they pass in and every
 * struct they receive; core never keeps a pointer past the call.
 *
 * Strings are passed as (pointer, length) and are NOT NUL-terminated by
 * contract; embedded NUL bytes are ordinary bytes. A NULL pointer is only valid
 * together with a length of 0.
 *
 * On any non-zero return the output struct, when not NULL, is zeroed (all
 * fields 0 are the safe values: UNKNOWN family, FAIL action, PERMANENT
 * category).
 *
 * Contract: docs/host-contract/luminalshine.md and docs/host-contract/pyrowave.md.
 */
#ifndef BOREALIS_HOST_H
#define BOREALIS_HOST_H

#include "borealis/core.h"

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Status codes returned by the functions that return int. */
#define BOREALIS_OK 0
#define BOREALIS_ERR_INVALID_ARGUMENT (-1) /* NULL out, NULL pointer with len > 0, unknown verb */
#define BOREALIS_ERR_INPUT_TOO_LARGE (-2) /* a string longer than its limit below */

/* Longest accepted strings, in bytes. The JNI layer passes at most this much UTF-8. */
#define BOREALIS_HOST_APPVERSION_MAX_LEN 64
#define BOREALIS_HOST_MESSAGE_MAX_LEN 256

/* borealis_host_caps.family */
#define BOREALIS_HOST_FAMILY_UNKNOWN 0
#define BOREALIS_HOST_FAMILY_GFE 1
#define BOREALIS_HOST_FAMILY_SUNSHINE 2

/* borealis_host_caps.codec_flags (derived from the SCM_* bits of ServerCodecModeSupport). */
#define BOREALIS_CODEC_HEVC 0x01u /* SCM_HEVC */
#define BOREALIS_CODEC_HEVC_MAIN10 0x02u /* SCM_HEVC_MAIN10 */
#define BOREALIS_CODEC_AV1 0x04u /* SCM_AV1_MAIN8 */
#define BOREALIS_CODEC_AV1_MAIN10 0x08u /* SCM_AV1_MAIN10 */
#define BOREALIS_CODEC_YUV444 0x10u /* any SCM_*_444 / REXT bit (SCM_MASK_YUV444) */

/* borealis_host_caps.pyrowave_profiles (ServerCodecModeSupport bits 0x00800000..0x04000000). */
#define BOREALIS_PYROWAVE_SDR_420 0x01u
#define BOREALIS_PYROWAVE_SDR_444 0x02u
#define BOREALIS_PYROWAVE_HDR_420 0x04u
#define BOREALIS_PYROWAVE_HDR_444 0x08u

  typedef struct borealis_host_caps {
    int32_t family; /* BOREALIS_HOST_FAMILY_* */
    int32_t version[4]; /* parsed appversion, all 0 when family is UNKNOWN */
    uint32_t codec_flags; /* BOREALIS_CODEC_* */
    uint32_t pyrowave_profiles; /* BOREALIS_PYROWAVE_* */
  } borealis_host_caps;

  /*
   * Parses the /serverinfo `appversion` text and the `ServerCodecModeSupport`
   * mask.
   *
   * appversion is exactly four dot-separated integers "a.b.c.d" with no
   * whitespace; only the fourth may be negative. A negative fourth component
   * (LuminalShine reports "7.1.431.-1") means SUNSHINE, a non-negative one GFE.
   * Anything else, including an empty string, is not an error: family is
   * UNKNOWN and version stays zero. codec_flags and pyrowave_profiles are
   * derived from codec_mask whatever the family; unknown mask bits (up to
   * 0x80000000) are ignored.
   *
   * Returns BOREALIS_OK, BOREALIS_ERR_INVALID_ARGUMENT (NULL out, or NULL
   * appversion with len > 0) or BOREALIS_ERR_INPUT_TOO_LARGE (len above
   * BOREALIS_HOST_APPVERSION_MAX_LEN).
   */
  BOREALIS_CORE_API int borealis_host_parse_caps(
    const char *appversion,
    size_t len,
    uint32_t codec_mask,
    borealis_host_caps *out
  );

/* borealis_launch_decide verb */
#define BOREALIS_LAUNCH_VERB_LAUNCH 0
#define BOREALIS_LAUNCH_VERB_RESUME 1

/* borealis_launch_decision.action */
#define BOREALIS_LAUNCH_ACTION_FAIL 0 /* stop and show the host message */
#define BOREALIS_LAUNCH_ACTION_RETRY 1 /* wait delay_ms, then repeat the same request */
#define BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH 2 /* /resume found no app: call /launch instead */

/* borealis_launch_decision.category */
#define BOREALIS_LAUNCH_CATEGORY_PERMANENT 0 /* never retried */
#define BOREALIS_LAUNCH_CATEGORY_PENDING 1 /* 409, another launch is pending */
#define BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY 2 /* 503, previous stream is restoring the display */
#define BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY 3 /* 503, GPU/display stack is not ready */
#define BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY 4 /* 503, capture/encoder/virtual-display failure */
#define BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP 5 /* 503 on /resume, nothing to resume */

  typedef struct borealis_launch_decision {
    int32_t action; /* BOREALIS_LAUNCH_ACTION_* */
    int32_t delay_ms; /* wait before the retry, 0 unless action is RETRY */
    int32_t category; /* BOREALIS_LAUNCH_CATEGORY_* */
    int32_t max_attempts; /* retries allowed in this category, 0 for PERMANENT and NO_RUNNING_APP */
  } borealis_launch_decision;

  /*
   * Decides what to do after /launch or /resume answered with a non-200
   * status_code.
   *
   * `verb` is BOREALIS_LAUNCH_VERB_LAUNCH or _RESUME; `status` is the XML
   * status_code; `msg` is the host message (at most
   * BOREALIS_HOST_MESSAGE_MAX_LEN bytes, NULL with len 0 when absent). The
   * message is matched as an ASCII case-insensitive substring, so it never
   * has to equal the host text.
   *
   * Core is stateless: the caller counts the retries it already made in the
   * decision's category and passes that as `attempts_in_category`, and owns the
   * overall time budget. When attempts_in_category reaches max_attempts the
   * decision is FAIL (category and max_attempts are still filled in).
   *
   * Returns BOREALIS_OK, BOREALIS_ERR_INVALID_ARGUMENT (NULL out, NULL msg
   * with len > 0, or an unknown verb) or BOREALIS_ERR_INPUT_TOO_LARGE.
   */
  BOREALIS_CORE_API int borealis_launch_decide(
    int32_t verb,
    int32_t status,
    const char *msg,
    size_t len,
    uint32_t attempts_in_category,
    borealis_launch_decision *out
  );

  /*
   * Returns the frame rate to put in the /launch `mode=WxHxFPS` parameter.
   *
   * A GFE host (is_gfe non-zero) keeps the upstream Moonlight rule: a
   * launch_fps above 60 becomes 0 (GFE would otherwise fall back to 720p60),
   * anything else is kept. Any other host gets stream_fps, the value the
   * client later announces as RTSP maxFPS, so /launch and RTSP agree and
   * LuminalShine does not rebuild the virtual display; a non-positive
   * stream_fps falls back to the GFE rule. The result is never negative (a
   * non-positive launch_fps yields 0, meaning "host default").
   *
   * Pure arithmetic, no ownership. Cannot fail.
   */
  BOREALIS_CORE_API int32_t borealis_launch_fps(int32_t is_gfe, int32_t launch_fps, int32_t stream_fps);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* BOREALIS_HOST_H */
