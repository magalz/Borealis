package io.github.magalz.borealis.designsystem.testing

import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.TextSpacing

/**
 * One run of a fit test: the screens in [font] at [spacing] (ADR 0058, decisions 2 and 4). Its name
 * is the name of the run in the test report.
 */
class FitCase(val font: FontChoice, val spacing: TextSpacing) {
    override fun toString() = "$font-$spacing"
}

/**
 * The runs of the fit tests at the largest text: every font at Normal and at Wider, the widest
 * level, and IBM Plex, the default, at Wide as well.
 */
fun fitCases(): List<FitCase> =
    FontChoice.entries.map { FitCase(it, TextSpacing.Normal) } +
        FitCase(FontChoice.Plex, TextSpacing.Wide) +
        FontChoice.entries.map { FitCase(it, TextSpacing.Wider) }
