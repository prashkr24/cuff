package com.srineere.airbridge.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
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
        packageName = "com.srineere.airbridge",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
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
