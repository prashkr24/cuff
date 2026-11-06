package com.srineere.airbridge.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records what a cold start and a look at the Health tab actually touch.
 *
 * The journey is deliberately the one somebody really does: open the app,
 * wait for the tab to settle, scroll the tiles once. Everything that path
 * touches gets compiled ahead of time on first launch instead of being
 * interpreted, which is where the startup win comes from.
 *
 * Run it with a phone plugged in:
 *
 *     ./gradlew :app:generateReleaseBaselineProfile
 *
 * It only produces a true profile on a device whose shell may grant
 * runtime permissions, because the run installs the app clean and the
 * Health tab needs its grants to draw anything. [requireDataOnScreen]
 * refuses to record when they are missing rather than writing a profile
 * of the empty state.
 *
 * It writes app/src/release/generated/baselineProfiles/, which is committed
 * and shipped. Re-record it when the startup path changes shape, not on
 * every edit: a stale profile is slower than none only if it is wrong about
 * most of the path.
 */
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startUpAndReadTheHealthTab() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        grantHealthReads()
        startActivityAndWait()
        requireDataOnScreen()
        // The tab reads Health Connect off the main thread, so the first
        // frame and the first filled frame are different moments and both
        // are worth having in the profile.
        device.waitForIdle()
        repeat(2) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 2 / 3,
                device.displayWidth / 2,
                device.displayHeight / 3,
                12,
            )
            device.waitForIdle()
        }
    }
}

/**
 * Tries to give the recorded run something to draw.
 *
 * Works on a stock ROM and does nothing on this phone, which is why
 * [requireDataOnScreen] exists underneath it rather than instead of it.
 * Health permissions are runtime permissions; granting one needs
 * GRANT_RUNTIME_PERMISSIONS, and on the OnePlus this app is developed
 * against the shell user does not have it -- the benchmark's own shell is
 * that same user, so the call comes back with a SecurityException. Hence
 * runCatching: on a device that allows it this is the whole fix, and on
 * this one it costs fifteen failed shell calls and the guard takes over.
 */
private fun MacrobenchmarkScope.grantHealthReads() {
    HEALTH_READS.forEach { permission ->
        runCatching {
            device.executeShellCommand("pm grant $PACKAGE android.permission.health.$permission")
        }
    }
}

/**
 * Stops a recording that would describe an app with nothing in it.
 *
 * Found by committing one. The profile is recorded against [PACKAGE],
 * which is a different install from the `.dev` build on the phone and
 * which the tooling installs clean for every run -- so it starts with no
 * Health Connect grants, the tab draws its "Allow Health Connect" card,
 * and the recording describes that. `HealthChartsKt` went from 72 rules to
 * **none**, `HealthScreenKt` from 196 to 16, and the file shrank by 15%:
 * the shape of a profile that has stopped describing the screen it exists
 * for. Nothing warned. A shorter profile looks like a tidier one.
 *
 * Granting by hand beforehand does not survive either. AGP uninstalls and
 * reinstalls rather than updating, so runtime grants go with it -- which
 * is worth knowing before anyone spends an evening on it, because the
 * permission really is there right up until the run starts.
 *
 * So: on a device where [grantHealthReads] is allowed, this never fires.
 * On one where it is not, there is no way to record a true profile without
 * driving Health Connect's own consent screens, and a wrong profile is
 * worse than a stale one -- a stale profile is wrong about the new code,
 * and this one was wrong about all of it.
 */
private fun MacrobenchmarkScope.requireDataOnScreen() {
    if (device.wait(Until.hasObject(By.textContains(UNGRANTED)), WAIT_MS) != null) {
        error(
            "The Health tab is showing its \"$UNGRANTED\" card, so this run would " +
                "record an app with no readings in it and no charts drawn. This phone " +
                "refuses pm grant from the shell, and a grant made by hand does not " +
                "survive the clean install this run performs. Keep the committed " +
                "profile instead: it was recorded with data on screen."
        )
    }
}

/** The row that only appears when nothing has been granted. */
private const val UNGRANTED = "Allow Health Connect"

/** Long enough for the tab's first read to come back off the IO thread. */
private const val WAIT_MS = 4_000L

/**
 * The app's install id, which is not its Kotlin package.
 *
 * Named once because the rename to Cuff moved one and not the other, and
 * a profile recorded against a package that no longer exists fails in a
 * way that looks like a device problem.
 */
private const val PACKAGE = "com.srineere.cuff"

/**
 * The thirteen reads, plus the two grants that widen them: history, which
 * is what makes a 90-day window mean anything, and background.
 *
 * Listed here rather than derived: this module cannot see the app's own
 * source, and a list that silently went stale would quietly stop granting
 * one and cost its chart without saying so. If a read is added to the
 * manifest, add it here too.
 */
private val HEALTH_READS = listOf(
    "READ_STEPS",
    "READ_SLEEP",
    "READ_HEART_RATE",
    "READ_RESTING_HEART_RATE",
    "READ_HEART_RATE_VARIABILITY",
    "READ_RESPIRATORY_RATE",
    "READ_SKIN_TEMPERATURE",
    "READ_VO2_MAX",
    "READ_EXERCISE",
    "READ_DISTANCE",
    "READ_TOTAL_CALORIES_BURNED",
    "READ_NUTRITION",
    "READ_WEIGHT",
    "READ_HEALTH_DATA_HISTORY",
    "READ_HEALTH_DATA_IN_BACKGROUND",
)
