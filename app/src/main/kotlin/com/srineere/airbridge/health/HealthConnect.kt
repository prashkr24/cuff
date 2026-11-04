package com.srineere.airbridge.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * Whether Health Connect can be reached at all.
 *
 * Three answers, not two: on Android 14 and up it is part of the platform and
 * always [Ready]; below that it is an app that may be missing or too old, and
 * those need different things said to the person.
 */
enum class HealthAvailability { Ready, NotInstalled, NeedsUpdate }

/**
 * The app's one way in to Health Connect.
 *
 * Everything here is read-only but for nutrition. That exception exists
 * because food is the only thing on the Health tab that nothing else writes:
 * a workout already in Health Connect would be double-counted by a second
 * copy, while an unlogged meal simply leaves the energy balance guesswork.
 */
object HealthConnect {

    /** Read for the whole tab. Asked for in one sheet, granted piecemeal. */
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** The one write, asked for only when the food log is first opened. */
    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
    )

    /**
     * Reading further back than thirty days, and reading while the app is off
     * screen. Both are separate grants the person can refuse, and refusing
     * either costs a feature rather than the tab: without history the windows
     * stop at thirty days, without background the goal buzz only catches up
     * when the app is opened.
     */
    val historyPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    val backgroundPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Everything the permission sheet asks for the first time. */
    val allPermissions: Set<String> =
        readPermissions + writePermissions + historyPermission + backgroundPermission

    fun availability(context: Context): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Ready
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsUpdate
            else -> HealthAvailability.NotInstalled
        }

    /** Null rather than a throw when Health Connect is not there to talk to. */
    fun clientOrNull(context: Context): HealthConnectClient? =
        if (availability(context) == HealthAvailability.Ready) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    /**
     * What has actually been granted. Asked before every read rather than
     * remembered, because Health Connect lets a person revoke a single data
     * type at any time and an app that caches the answer starts lying.
     */
    suspend fun granted(client: HealthConnectClient): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
}
