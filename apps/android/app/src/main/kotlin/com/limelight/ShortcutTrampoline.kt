package com.limelight

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.magalz.borealis.library.shell.LibraryActivity
import io.github.magalz.borealis.library.shortcuts.ShortcutIntentParser

/**
 * The entry of every launcher shortcut, new and pinned by 12.2, so the pinned ones keep working. It
 * only forwards the extras to the Library as a request and finishes; the Library shows offline,
 * unpaired and Wake-on-LAN. (BOREALIS: this replaces the `PcView` and `AppView` stack it started.)
 */
class ShortcutTrampoline : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = ShortcutIntentParser.parse(intent.extras)
        startActivity(
            Intent(this, LibraryActivity::class.java)
                .putExtras(request.toExtras())
                // A running Library gets the request in onNewIntent instead of a second copy.
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
