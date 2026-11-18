package com.activebook.autoclick.app.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import java.io.File

/**
 * Debug-only breadcrumb trail for the accessibility hint window.
 *
 * Appends one line per lifecycle event to `files/hint_trace.log`, which is readable on a debug build
 * with `adb shell run-as com.activebook.autoclick.app cat files/hint_trace.log`. Kept as a file rather
 * than logcat because the OEM suppresses this app's own `Log.*` output.
 */
internal object HintTrace {

    private const val FILE_NAME = "hint_trace.log"

    fun log(context: Context, line: String) {
        val debuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        runCatching {
            File(context.filesDir, FILE_NAME)
                .appendText("${SystemClock.elapsedRealtime()} $line\n")
        }
    }
}
