package com.bam.incomedy.feature.auth.mvi

import platform.Foundation.NSLog

/**
 * Writes shared auth events into the native Apple logging stream.
 */
internal actual fun logClientAuthEvent(message: String) {
    NSLog("AUTH_FLOW $message")
}
