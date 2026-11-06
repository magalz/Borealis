package io.github.magalz.borealis.input.mouserelease

import io.github.magalz.borealis.input.RecordingConnection

/** What the PC got of the mouse buttons, in order: no pointer moves, no scrolls. */
internal fun RecordingConnection.buttonEvents(): List<String> = mouse.filter {
    it.startsWith("down") || it.startsWith("up")
}
