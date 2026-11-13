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
        // Nothing to do with the health half switched off: everything
        // below reads Health Connect, and the two things this moment is
        // for -- the report and the settled readiness figure -- are both
        // health features.
        //
        // Returning without booking is what ends the chain. Each run
        // books the next, so a run that books nothing is the last one,
        // and a phone carrying a booking made before the switch existed
        // loses it the first morning after.
        if (!HealthEnabled.isOn(applicationContext)) {
            Log.i(TAG, "morning: the health half is off, so nothing is booked")
            return Result.success()
        }

        // Tomorrow's is booked before today's is sent, so a failure to read
        // or to notify costs one morning rather than every morning after it.
        //
        // Booked whether or not the report is switched on, which it was not
        // before. Readiness rides this moment now, and readiness belongs to
        // anybody with the tile on their tab rather than only to the people
        // who asked for a notification about it.
        MorningMoment.schedule(applicationContext)

        val settings = HealthSettings.morning(applicationContext)
        val zone = ZoneId.systemDefault()
        val clock = LocalTime.now(zone)
        val today = LocalDate.now(zone).toEpochDay()

        // One read for both halves of this moment, and deliberately the
        // short one. Thirty days is what the report draws, and it is also
        // all it takes to find out whether the night and the morning's
        // readings have arrived; the hundred and twenty days readiness is
        // counted against are read inside settleReadiness, on the one pass
        // that can finish it.
        val snapshot = runCatching { HealthReader().read(applicationContext, 30) }.getOrNull()

        // Readiness first, and whatever the report setting says: this is the
        // half the Readiness screen is waiting on, and somebody who has
        // never wanted a notification still opens the tab.
        val settled = snapshot != null && settleReadiness(applicationContext, snapshot, today)
        val nightWritten = snapshot?.nights?.maxByOrNull { it.day }?.day == today
        if (snapshot != null) sendReport(settings, snapshot, zone, clock, today, nightWritten)

        // Last in the method, and it has to be. Looking again replaces a
        // unique work whose name this very run may be carrying -- a retry
        // re-booking itself -- and replacing running work stops it where it
        // stands. Anything written after this line is not guaranteed to
        // happen at all.
        //
        // One decision with two reasons behind it: the night is what the
        // report waits for and the morning's readings are what readiness
        // waits for. Both arrive when the band syncs, which is not a thing
        // this app can hurry, so it comes back and asks again.
        //
        // Nothing read in thirty days is not a morning running late, it is
        // a phone nothing writes to, and coming back every twenty minutes
        // until eleven would be fifteen wakeups to re-ask a question that
        // has no answer in it. A read that *threw* is different and is
        // worth another look: Health Connect may simply have been updating.
        val worthWaitingFor = snapshot == null || snapshot.hrv.isNotEmpty() ||
            snapshot.nights.isNotEmpty() || snapshot.steps.isNotEmpty()
        if (worthWaitingFor && (!settled || !nightWritten)) {
            Log.i(TAG, "morning: waiting, readiness=$settled night=$nightWritten")
            MorningMoment.lookAgainBefore(applicationContext, clock)
        }
        return Result.success()
    }

    /** The notification half, which is only for people who asked for it. */
    private fun sendReport(
        settings: MorningSettings,
        snapshot: HealthSnapshot,
        zone: ZoneId,
        clock: LocalTime,
        today: Long,
        nightWritten: Boolean,
    ) {
        if (!settings.on) return

        // Quiet hours are a promise about this phone, not only about the
        // band: somebody who has said "nothing between 22:00 and 07:00"
        // has not carved out an exception for good news.
        val silence = runCatching {
            RelayRuleStore.read(applicationContext).silence.reason(System.currentTimeMillis())
        }.getOrNull()
        if (silence != null) {
            Log.i(TAG, "morning: skipped by $silence")
            return
        }

        // One a day. A reschedule after a reboot can land twice in the same
        // morning, and two identical reports is one too many.
        if (HealthSettings.morningSentDay(applicationContext) == today) {
            Log.i(TAG, "morning: already sent today")
            return
        }

        // Waiting for the night rather than for the clock. Google Health
        // writes it when the band syncs, so the check holds the report
        // rather than sending one with the hole in it; the next look is
        // booked by the caller once this has returned.
        if (settings.afterWaking && !nightWritten) {
            if (clock.hour < MorningMoment.LATEST_HOUR) {
                Log.i(TAG, "morning: no night written yet, holding the report")
                return
            }
            // Late enough that waiting longer is waiting for nothing. What
            // there is goes out, if there is anything at all.
            Log.i(TAG, "morning: no night by ${MorningMoment.LATEST_HOUR}:00, sending without it")
        }

        val report = morningReport(
            snapshot,
            HealthGoals.read(applicationContext),
            // Reads the figure settled above rather than working out a
            // second one. This snapshot is thirty days and sixty readings
            // is the gate, so a report left to compute its own could only
            // ever say "calibrating" -- which it did, every morning, until
            // the settled figure was there to be read.
            readinessToday(applicationContext, snapshot),
            zone,
        )
        if (!report.worthSending) {
            Log.i(TAG, "morning: nothing read, so nothing sent")
            return
        }
        MorningMoment.notify(applicationContext, report)
        HealthSettings.morningSent(applicationContext, today)
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
     *
     * **It no longer cancels when the report is switched off.** The report
     * used to be the only thing this moment carried, and turning it off
     * meant nothing happened in the morning at all -- which is why
     * readiness was only ever worked out by whoever opened the screen and
     * waited. Two things want the moment now, and only one of them is a
     * notification somebody opted into. The cost is one wakeup a morning
     * on a phone that wanted no report; the alternative was a fourth
     * scheduled job doing the same look a few minutes apart.
     */
    fun schedule(context: Context) {
        // The whole moment belongs to the health half, report or no
        // report: it reads thirty days out of Health Connect and settles
        // readiness from them. With that half off there is nothing to
        // read and nothing it may say, so this cancels what is booked
        // rather than booking another -- which also means every caller
        // can go on calling it unconditionally and get the right answer.
        if (!HealthEnabled.isOn(context)) {
            cancel(context)
            Log.i("AirBridge", "morning: the health half is off, booking cancelled")
            return
        }
        val settings = HealthSettings.morning(context)
        // In waking mode the booking is for the first look rather than for
        // the report: what time it actually goes out is decided by when the
        // night turns up. With the report off there is no clock to honour,
        // so the look is the only thing being booked and it goes early.
        val delay = untilNext(
            if (settings.on && !settings.afterWaking) settings.minute else EARLIEST_MINUTE,
        )
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MorningWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build(),
        )
        Log.i("AirBridge", "morning: next in $delay min, report on=${settings.on}")
    }

    /**
     * Drops the morning entirely: the daily booking and the retry both.
     *
     * Both names, and that is not belt and braces. [lookAgainBefore]
     * deliberately books under a second one so that a retry cannot
     * cancel tomorrow's run -- so cancelling only the first would leave a
     * retry to fire once more, and the first thing a run does is book the
     * whole chain again.
     */
    fun cancel(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(NAME)
            manager.cancelUniqueWork("$NAME-again")
        }
    }

    /**
     * Comes back in twenty minutes, because what it was waiting for was
     * not there yet -- and stops asking once the morning is over.
     *
     * The hour is checked here rather than at each call site now that two
     * things wait on this moment: a retry booked by one of them past
     * [LATEST_HOUR] would be the other one still being asked long after it
     * had given up.
     *
     * Separate work from the daily booking, and deliberately so: the daily
     * one is already booked for tomorrow by the time this is enqueued, and
     * sharing a name would have the retry cancel it.
     */
    fun lookAgainBefore(context: Context, clock: LocalTime = LocalTime.now()) {
        if (clock.hour >= LATEST_HOUR) return
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
                    .setSmallIcon(R.drawable.ic_cuff_notification)
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
