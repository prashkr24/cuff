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

    fun schedule(context: Context) {
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
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
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
