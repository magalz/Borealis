package io.github.magalz.borealis.host.video

/** What the stream's HEVC SPS says about the profile, chroma format and bit depth. */
data class HevcSpsInfo(
    val profileIdc: Int,
    /** `general_profile_compatibility_flag[j]` is bit `j`. */
    val compatibilityFlags: Int,
    val chromaFormatIdc: Int,
    val separateColourPlane: Boolean,
    val bitDepthLuma: Int,
    val bitDepthChroma: Int,
)
