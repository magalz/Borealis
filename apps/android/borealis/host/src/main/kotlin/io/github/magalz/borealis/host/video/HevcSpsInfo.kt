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
) {
    /** Main 10 is the lowest profile that carries more than 8 bits. */
    val needsMain10: Boolean
        get() = bitDepthLuma > 8 || bitDepthChroma > 8

    val profileName: String
        get() =
            when (profileIdc) {
                1 -> "Main"
                2 -> "Main10"
                3 -> "MainStillPicture"
                4 -> "RExt"
                5 -> "HighThroughput"
                9 -> "SCC"
                else -> "idc$profileIdc"
            }

    val chromaName: String
        get() =
            when (chromaFormatIdc) {
                0 -> "4:0:0"
                1 -> "4:2:0"
                2 -> "4:2:2"
                else -> if (separateColourPlane) "4:4:4-separate-planes" else "4:4:4"
            }

    fun describe(): String =
        "profile=$profileName chroma=$chromaName depth=$bitDepthLuma/$bitDepthChroma"
}
