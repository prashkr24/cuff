package com.srineere.airbridge

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What went wrong, kept on the phone until somebody chooses to send it.
 *
 * This app is unofficial and rides a protocol that a firmware update can
 * change underneath it, so the interesting failures happen on somebody else's
 * phone, days later, with nobody watching logcat. A crash that leaves no
 * trace is a crash that can only be described as "it stopped working".
 *
 * Two halves, deliberately apart:
 *
 * - **Recording is automatic**, because a log you have to switch on before
 *   the crash is a log you never have. It is a file in this app's own private
 *   storage, which is no more exposed than the logcat line it replaces, and
 *   nothing reads it but this app.
 * - **Sending is not.** Nothing leaves the phone unless somebody presses the
 *   button, and the button hands the text to Android's share sheet rather
 *   than posting it anywhere, so the person picks the destination and sees
 *   what they are sending. That keeps the promise the About screen makes.
 */
object CrashLog {

    private const val TAG = "AirBridge"
    private const val FILE = "crash.log"

    /**
     * Entries are separated by a record character rather than a blank line,
     * because a stack trace contains blank lines and a delimiter that can
     * appear inside the thing it delimits is not a delimiter.
     */
    private const val SEPARATOR = '\u001e'

    /**
     * Roughly how much to keep. A crash loop can write the same trace a
     * hundred times in a minute, and the useful part is the first one and the
     * fact that it repeated -- not ten megabytes of the middle.
     */
    private const val MAX_BYTES = 128 * 1024
    private const val MAX_ENTRIES = 20

    @Volatile
    private var installed = false

    private fun file(context: Context) = File(context.filesDir, FILE)

    /**
     * Catch what nothing else caught.
     *
     * Called from both entry points because the relay outlives the activity
     * and often runs without it: whichever starts the process first installs
     * this, and the second call does nothing. The previous handler is always
     * invoked afterwards -- ours records, Android's still ends the process,
     * and swallowing that would turn a crash into a freeze.
     */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Nothing in here may throw. A handler that fails while handling
            // a failure loses the original, which is the one that mattered.
            runCatching { record(app, error, thread.name) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Note something that went wrong without taking the app down with it.
     *
     * The transport swallows a lot by design -- a band out of range is not a
     * crash -- and this is for the ones worth keeping anyway.
     */
    fun note(context: Context, what: String, error: Throwable? = null) {
        runCatching { record(context.applicationContext, error, thread = null, what = what) }
    }

    private fun record(context: Context, error: Throwable?, thread: String?, what: String? = null) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            append(stamp)
            if (what != null) append("  ").append(what)
            if (thread != null) append("  on ").append(thread)
            appendLine()
            append(BuildConfig.VERSION_NAME)
            append("  ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append("  Android ").append(Build.VERSION.RELEASE)
            appendLine()
            if (error != null) append(Log.getStackTraceString(error))
        }

        val target = file(context)
        val text = merge(read(context), body)
        runCatching { target.writeText(text) }
            .onFailure { Log.w(TAG, "could not write the crash log", it) }
    }

    /**
     * The file's new contents: the old entries, the new one, and nothing
     * older than the caps allow.
     *
     * Pure, and separate from the file, because this is the part with
     * arithmetic in it and the part that can lose somebody's first crash.
     * Trimming is always from the **front**: the newest entry is the one
     * being written, and a size cap that dropped it would throw away the
     * reason the function was called.
     */
    internal fun merge(existing: List<String>, entry: String): String {
        val kept = existing.takeLast(MAX_ENTRIES - 1) + entry
        var text = kept.joinToString(SEPARATOR.toString())
        while (text.toByteArray().size > MAX_BYTES && text.contains(SEPARATOR)) {
            text = text.substringAfter(SEPARATOR)
        }
        return text
    }

    /** Newest last, which is the order a person reads them in. */
    fun read(context: Context): List<String> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return runCatching {
            target.readText().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun count(context: Context): Int = read(context).size

    /** The whole log as one block, which is what gets shared. */
    fun asText(context: Context): String = read(context).joinToString("\n\n")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
