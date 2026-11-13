package com.srineere.airbridge.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.srineere.airbridge.MainActivity
import com.srineere.airbridge.R
import com.srineere.airbridge.RelayRuleStore
import com.srineere.airbridge.widget.ROUTE_EXTRA
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Whether the journal asks at the end of the day, and when. */
data class JournalSettings(
    val on: Boolean = false,
    /** Minute of the day it asks at. Half nine by default. */
    val minute: Int = 21 * 60 + 30,
) {
    val hour: Int get() = minute / 60
    val past: Int get() = minute % 60
    val clock: String get() = "%02d:%02d".format(hour, past)
}

/**
 * One ask a day, before bed.
 *
 * The evening is when a journal is worth asking: caffeine at five and a
 * screen in bed are things you still remember at half nine and have half
 * forgotten by morning. Answering then also attaches the night to the one
 * that is about to happen rather than the one that already has.
 */
class JournalWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Hidden: see [JournalReminder.hidden]. A booking made by a build
        // that still offered the journal can still land here, and the
        // answer is to do nothing and book nothing -- each run books the
        // next, so a run that books nothing is the last of them.
        //
        // Not a cancel. This run *is* the unique work it would cancel,
        // and replacing running work stops it where it stands; the
        // booking is dropped properly by [JournalReminder.cancel] on the
        // next launch of the app.
        if (JournalReminder.hidden) {
            Log.i(TAG, "journal: hidden, so nothing is asked and nothing is booked")
            return Result.success()
        }

        val settings = HealthSettings.journal(applicationContext)
        if (!settings.on) return Result.success()
        // Tomorrow before tonight, so one failure costs one evening.
        JournalReminder.schedule(applicationContext)

        val silence = runCatching {
            RelayRuleStore.read(applicationContext).silence.reason(System.currentTimeMillis())
        }.getOrNull()
        if (silence != null) {
            Log.i(TAG, "journal: skipped by $silence")
            return Result.success()
        }

        val night = Journal.nightFor()
        val answered = Journal.read(applicationContext, night)
        // Nothing to ask about a night already answered in full, and asking
        // anyway is how a daily prompt becomes a thing people turn off.
        if (answered.size >= JournalTag.entries.size) {
            Log.i(TAG, "journal: already answered")
            return Result.success()
        }
        JournalReminder.notify(applicationContext, answered.size)
        return Result.success()
    }

    private companion object {
        const val TAG = "AirBridge"
    }
}

object JournalReminder {
    private const val NAME = "air-journal"
    private const val CHANNEL = "journal_reminder"
    private const val NOTIFICATION = 905

    const val ROUTE = "journal"

    /**
     * Whether the journal is offered at all.
     *
     * **Hidden, not removed.** It was built a week ago and is being
     * thought better of rather than decided against, so every part of it
     * is still in the tree and still compiles: this file, `Journal.kt`,
     * `JournalScreen.kt`, the settings in `HealthSettings`, and
     * `JournalTest`. The answers anybody has already written are
     * untouched on disk, which is what makes unhiding free.
     *
     * What is gone is every way in. The row on the Health tab, the
     * booking below that raised the evening notification, and the route,
     * which `isHiddenRoute` turns away so that a notification posted by
     * an older build cannot open a screen nothing else links to. Putting
     * the row back and taking the route out of that set is the whole of
     * bringing it back; deleting it is a separate day's work and
     * undoing a deletion is not free.
     *
     * Deliberately not a `const`. A constant true would let the compiler
     * fold the booking below away as unreachable, and the booking is the
     * thing being kept.
     */
    internal val hidden: Boolean = true

    fun schedule(context: Context) {
        if (hidden) {
            cancel(context)
            return
        }
        val settings = HealthSettings.journal(context)
        val manager = WorkManager.getInstance(context)
        if (!settings.on) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val delay = MorningMoment.untilNext(settings.minute)
        manager.enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<JournalWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build(),
        )
        Log.i("AirBridge", "journal: next in $delay min, at ${settings.clock}")
    }

    /**
     * Drops the booking, and anything it has already put in the shade.
     *
     * The notification matters as much as the work does. A reminder
     * posted last night is still sitting there when this build goes on,
     * and tapping it would ask for a screen that is no longer offered --
     * so the one that is up goes with the one that would have followed
     * it.
     */
    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        }
    }

    /**
     * The ask, with the count of what is left in it.
     *
     * "Six things" and "two things" are different sized favours, and the
     * one that is nearly done is the one worth finishing.
     */
    fun notify(context: Context, answered: Int) {
        val left = JournalTag.entries.size - answered
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Journal reminder",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                5,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(ROUTE_EXTRA, ROUTE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL)
            } else {
                @Suppress("DEPRECATION") Notification.Builder(context)
            }
            manager.notify(
                NOTIFICATION,
                builder
                    .setSmallIcon(R.drawable.ic_cuff_notification)
                    .setContentTitle("How was today?")
                    .setContentText(
                        if (answered == 0) {
                            "${JournalTag.entries.size} things to answer before bed"
                        } else {
                            "$left left to answer before bed"
                        },
                    )
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }.onFailure { Log.i("AirBridge", "journal: could not notify, ${it.message}") }
    }
}
