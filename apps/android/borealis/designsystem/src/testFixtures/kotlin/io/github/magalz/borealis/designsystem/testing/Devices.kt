package io.github.magalz.borealis.designsystem.testing

/** A phone held upright: the Robolectric qualifier the phone layouts are tested at. */
const val PHONE = "w360dp-h640dp-xhdpi"

/** A phone held sideways, which is how a stream is played: a panel gets 52% of 640dp. */
const val PHONE_LANDSCAPE = "w640dp-h360dp-land-xhdpi"

/** A handheld console: a wide landscape window with a touch screen. */
const val HANDHELD = "w800dp-h480dp-land-hdpi"

/** A handheld console as the settings screen sees it: no touch screen, so no touch targets. */
const val HANDHELD_NO_TOUCH = "w800dp-h480dp-land-hdpi-notouch"

/** A television: a landscape window the Android TV configuration applies to. */
const val TV = "w960dp-h540dp-land-television-xhdpi"
