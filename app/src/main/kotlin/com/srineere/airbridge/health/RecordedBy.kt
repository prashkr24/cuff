package com.srineere.airbridge.health

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata

/**
 * The hardware a record says it came from.
 *
 * Health Connect attaches this to every record and nothing in this app read
 * it until now, which left the tab unable to tell a run recorded on a watch
 * from one typed into a phone -- and left the Device tab's icon chosen by a
 * guess at a model name.
 *
 * All three fields are optional in Health Connect and plenty of writers
 * fill none of them, so this is null far more often than not. A row with no
 * device says nothing rather than guessing, because "unknown" and "your
 * phone" are different facts and only one of them is true.
 */
data class RecordedBy(
    /** One of `Device.TYPE_*`. */
    val type: Int,
    val manufacturer: String?,
    val model: String?,
) {
    /**
     * What to call it.
     *
     * The model alone where it already carries its maker -- "Pixel Watch 2"
     * does not want "Google" in front of it -- and the pair where it does
     * not, which is how a Garmin arrives. With no model at all the type is
     * the only honest name left: "Garmin watch" says what is true and
     * stops.
     */
    val label: String
        get() {
            val made = manufacturer?.trim()?.takeIf { it.isNotEmpty() }
            val name = model?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                name != null && made != null && !name.startsWith(made, ignoreCase = true) ->
                    "$made $name"
                name != null -> name
                made != null -> "$made $word"
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }

    /** The bare kind, for when there is no name to put in front of it. */
    val word: String
        get() = when (type) {
            Device.TYPE_WATCH -> "watch"
            Device.TYPE_FITNESS_BAND -> "band"
            Device.TYPE_RING -> "ring"
            Device.TYPE_CHEST_STRAP -> "chest strap"
            Device.TYPE_SCALE -> "scale"
            Device.TYPE_PHONE -> "phone"
            Device.TYPE_HEAD_MOUNTED -> "headset"
            Device.TYPE_SMART_DISPLAY -> "display"
            else -> "device"
        }

    /** Whether this is worth drawing at all. */
    val known: Boolean
        get() = type != Device.TYPE_UNKNOWN || manufacturer != null || model != null
}

/**
 * What a record's metadata says about the hardware, or null when it says
 * nothing worth repeating.
 */
fun Metadata.recordedBy(): RecordedBy? {
    val device = this.device ?: return null
    val found = RecordedBy(
        type = device.type,
        manufacturer = device.manufacturer,
        model = device.model,
    )
    return found.takeIf { it.known }
}
