package com.limelight

import android.app.Activity
import android.os.Bundle

/** The entry of every launcher shortcut, new and pinned by 12.2. */
class ShortcutTrampoline : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
