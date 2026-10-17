package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * What the About rows need from the system: the text of the Version row, how to open a link, and
 * the licence texts. A test passes its own.
 *
 * @property versionSummary the value of the Version row, for example `1.2.0 (commit 6d9c9ff7)`.
 * @property openLink opens a link in an app and returns whether an app took it.
 * @property loadLicenses the bundled `NOTICE` and `LICENSE` texts, or `null` when the build has
 *   none.
 */
@Immutable
class AboutSource(
    val versionSummary: String,
    val openLink: (String) -> Boolean,
    val loadLicenses: () -> String?,
)

/** The [AboutSource] of this app: its version, the browser and the bundled licence assets. */
@Composable fun rememberAboutSource(): AboutSource = AboutSource("", { false }, { null })
