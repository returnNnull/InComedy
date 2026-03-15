package com.bam.incomedy.feature.auth.mvi

import android.util.Log

/**
 * Writes shared auth events into Android Logcat with a stable tag for device-side debugging.
 */
internal actual fun logClientAuthEvent(message: String) {
    Log.i("AUTH_FLOW", message)
}
