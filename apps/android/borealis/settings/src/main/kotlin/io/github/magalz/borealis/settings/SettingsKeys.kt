package io.github.magalz.borealis.settings

import io.github.magalz.borealis.logging.LogStorage

/** Keys of the Borealis settings in the default SharedPreferences (the `borealis_` prefix). */
object SettingsKeys {
    const val VERBOSE_LOGGING = LogStorage.VERBOSE_LOGGING_KEY
    const val MASK_ADDRESSES = LogStorage.MASK_ADDRESSES_KEY
    const val ABOUT_VERSION = "borealis_about_version"
    const val ABOUT_SOURCE_CODE = "borealis_about_source_code"
    const val ABOUT_LICENSE = "borealis_about_license"
    const val ABOUT_LICENSES = "borealis_about_licenses"
    const val ABOUT_PRIVACY_POLICY = "borealis_about_privacy_policy"
}
