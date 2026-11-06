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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Whether the morning report is sent, and what decides when. */
data class MorningSettings(
    val on: Boolean = false,
    /** Minute of the day it is sent at, when it is sent by the clock. */
    val minute: Int = 7 * 60 + 10,
    /**
     * Sent once the night has been written rather than at a fixed hour.
     *
     * The honest version of "after you wake up". Nothing here can see you
     * open your eyes; what it can see is Google Health writing the night,
     * which happens when your band next syncs -- usually within minutes of
     * you picking the phone up. So the report waits for the night to exist
     * and goes out then, which is both later and more useful than a clock
     * that fires while the band is still holding last night to itself.
     */
    val afterWaking: Boolean = true,
) {
    val hour: Int get() = minute / 60
    val past: Int get() = minute % 60
    val clock: String get() = "%02d:%02d".format(hour, past)
}

/**
 * One notification a day, at a time the person set.
 *
 * WorkManager rather than an alarm: this is a summary of a night that is
 * already over, so a few minutes' drift costs nothing, and an exact alarm
 * would be asking for a permission to deliver something that is not urgent.
 * WorkManager also survives a reboot without a receiver of this app's own.
 *
 * Each run schedules the next one rather than repeating on a period.
 * Periodic work has a floor and a flex window that would wander off the
 * hour somebody chose; a one-shot a day lands where it was asked to.
 */
class MorningWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = HealthSettings.morning(applicationContext)
        if (!settings.on) return Result.success()
        // Tomorrow's is booked before today's is sent, so a failure to read
        // or to notify costs one morning rather than every morning after it.
        MorningMoment.schedule(applicationContext)

        val zone = ZoneId.systemDefault()
        val clock = LocalTime.now(zone)
        // Quiet hours are a promise about this phone, not only about the
        // band: somebody who has said "nothing between 22:00 and 07:00"
        // has not carved out an exception for good news.
        val silence = runCatching {
            RelayRuleStore.read(applicationContext).silence.reason(System.currentTimeMillis())
        }.getOrNull()
        if (silence != null) {
            Log.i(TAG, "morning: skipped by $silence")
            return Result.success()
        }

        val already = HealthSettings.morningSentDay(applicationContext)
        val today = LocalDate.now(zone).toEpochDay()
        // One a day. A reschedule after a reboot can land twice in the same
        // morning, and two identical reports is one too many.
        if (already == today) {
            Log.i(TAG, "morning: already sent today")
            return Result.success()
        }

        val snapshot = runCatching { HealthReader().read(applicationContext, 30) }.getOrNull()
            ?: return Result.success()
        val report = morningReport(snapshot, HealthGoals.read(applicationContext), zone)

        // Waiting for the night rather than for the clock. Google Health
        // writes it when the band syncs, so the check comes back and asks
        // again rather than sending a report with the hole in it.
        if (settings.afterWaking && report.night?.day != today) {
            if (clock.hour < MorningMoment.LATEST_HOUR) {
                MorningMoment.lookAgain(applicationContext)
                Log.i(TAG, "morning: no night written yet, looking again shortly")
                return Result.success()
            }
            // Late enough that waiting longer is waiting for nothing. What
            // there is goes out, if there is anything at all.
            Log.i(TAG, "morning: no night by ${MorningMoment.LATEST_HOUR}:00, sending without it")
        }

        if (!report.worthSending) {
            Log.i(TAG, "morning: nothing read, so nothing sent")
            return Result.success()
        }
        MorningMoment.notify(applicationContext, report)
        HealthSettings.morningSent(applicationContext, today)
        return Result.success()
    }

    private companion object {
        const val TAG = "AirBridge"
    }
}

object MorningMoment {
    private const val NAME = "air-morning"
    private const val CHANNEL = "morning_report"
    private const val NOTIFICATION = 904

    /**
     * When the day's first look happens, waiting for the night.
     *
     * Early enough for anybody who is up at six, and not so early that a
     * phone spends the small hours asking Health Connect a question whose
     * answer cannot have changed.
     */
    const val EARLIEST_MINUTE = 6 * 60

    /** How often it looks again, and the hour it stops looking. */
    const val LOOK_AGAIN_MINUTES = 20L
    const val LATEST_HOUR = 11

    /** The route the notification opens, and the screen's own name. */
    const val ROUTE = "morning"

    /**
     * Books the next one, replacing whatever was booked before.
     *
     * Called when the setting changes and again from each run, so there is
     * exactly one booking at any moment whatever has happened to the phone.
     */
    fun schedule(context: Context) {
        val settings = HealthSettings.morning(context)
        val manager = WorkManager.getInstance(context)
        if (!settings.on) {
            manager.cancelUniqueWork(NAME)
            return
        }
        // In waking mode the booking is for the first look rather than for
        // the report: what time it actually goes out is decided by when the
        // night turns up.
        val delay = untilNext(if (settings.afterWaking) EARLIEST_MINUTE else settings.minute)
        manager.enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MorningWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build(),
        )
        Log.i("AirBridge", "morning: next in $delay min, at ${settings.clock}")
    }

    /**
     * Comes back in twenty minutes, because the night was not there yet.
     *
     * Separate work from the daily booking, and deliberately so: the daily
     * one is already booked for tomorrow by the time this is enqueued, and
     * sharing a name would have the retry cancel it.
     */
    fun lookAgain(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$NAME-again",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MorningWorker>()
                .setInitialDelay(LOOK_AGAIN_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Minutes from now until that minute of the day next comes round. */
    internal fun untilNext(minuteOfDay: Int, now: LocalTime = LocalTime.now()): Long {
        val nowMinute = now.hour * 60 + now.minute
        val gap = minuteOfDay - nowMinute
        // Never zero: a run that booked itself for "right now" would spin.
        return if (gap > 0) gap.toLong() else (gap + 24 * 60).toLong()
    }

    /**
     * The report on the lock screen: the headline, then the figures.
     *
     * One id, so a morning that somehow sends twice replaces itself rather
     * than stacking. Tapping it opens the same screen the Health tab does,
     * because the notification is a summary and not a second copy.
     */
    fun notify(context: Context, report: MorningReport) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Morning report",
                        // Low: it arrives while you are waking up, and a
                        // summary of a finished night has not earned a sound.
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                4,
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
                    .setSmallIcon(android.R.drawable.ic_menu_day)
                    .setContentTitle(report.headline)
                    .setContentText(report.line)
                    // The second line is long enough to be cut on a lock
                    // screen, and it is the half with the figures in it.
                    .setStyle(Notification.BigTextStyle().bigText(report.line))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }.onFailure { Log.i("AirBridge", "morning: could not notify, ${it.message}") }
    }
}
