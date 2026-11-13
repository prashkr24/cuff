package com.srineere.airbridge.health

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Whether this app reads your health at all.
 *
 * Half of CUFF is a relay and half of it is a set of readings, and the
 * people who tried it did not all want the second half. A band that buzzes
 * for a message is a complete thing on its own, and for somebody who only
 * wanted that, the Health tab is a consent sheet, thirteen permissions and
 * a page of empty cards charged against a feature they were never going to
 * open. So the readings are opt-in, and the switch sits on the Device tab
 * beside the other facts about this install.
 *
 * Off by default -- but only on a **fresh** install. An install that is
 * already reading, because Health Connect has granted it something or
 * because somebody has arranged their tiles, comes up with the half on.
 * Switching off a working setup is not a default, it is a regression. See
 * [settle], which decides that once and writes the answer down so the
 * question is never asked again.
 *
 * Stored in `air_health` with everything else the health half keeps, and
 * published as a flow the way [com.srineere.airbridge.ui.AppSettings] is:
 * the bottom bar gains and loses a tab with it, and a bar that only
 * noticed on the next cold start would be a switch that looked broken.
 * Everything outside the UI -- the relay, the widgets, the two workers --
 * reads [isOn] straight off the file instead. They have no composition to
 * collect in, they run in this same process, and the file is the truth
 * either way.
 */
object HealthEnabled {
    private const val PREFS = "air_health"
    private const val KEY = "health_on"

    /**
     * Whether the question has been answered for this install.
     *
     * A second key rather than a third state folded into the first,
     * because the answer is a plain boolean at every other read and a
     * nullable one would have to be unwrapped at all of them. It also
     * makes the one promise this file has to keep legible: once this is
     * true, nothing asks Health Connect about it again.
     */
    private const val KEY_SETTLED = "health_on_settled"

    private val state = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = state.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored answer, for everything that cannot collect a flow. */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    /** Brings the flow up to what is on disk. Called once, from the activity. */
    fun load(context: Context) {
        state.value = isOn(context)
    }

    /**
     * The switch being flipped by hand.
     *
     * Settled as well as set: somebody who has answered the question
     * themselves must not have it answered for them on the next launch,
     * and that includes the person who turns it straight back off.
     */
    fun set(context: Context, on: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY, on)
            .putBoolean(KEY_SETTLED, true)
            .apply()
        state.value = on
    }

    /**
     * Decides, once, what an install that predates the switch should do.
     *
     * The arithmetic is [healthOnDecision]; this is the part that has to
     * touch a phone. It asks Health Connect what it has granted, which is
     * the only way to tell a person who has been using the Health tab for
     * a fortnight from one who has just installed the app -- and it is a
     * suspend call to an interface that can be mid-update, so there is a
     * third answer besides yes and no, and [healthOnDecision] takes it.
     *
     * An answer it could not get is not written down. The alternative is
     * to decide "off" on a phone whose Health Connect happened to be
     * updating at that second and then never look again, which is exactly
     * the regression this whole function exists to prevent; the cost of
     * waiting is that one launch comes up with the half off and the next
     * one gets it right.
     *
     * Called from the activity's background pass, before anything that
     * asks [isOn].
     */
    suspend fun settle(context: Context) {
        val store = prefs(context)
        if (store.getBoolean(KEY_SETTLED, false)) {
            state.value = store.getBoolean(KEY, false)
            return
        }
        val decision = healthOnDecision(
            settled = false,
            grantedReads = grantedReads(context),
            tilesConfigured = HealthSettings.tilesConfigured(context),
        ) ?: return
        store.edit().putBoolean(KEY, decision).putBoolean(KEY_SETTLED, true).apply()
        state.value = decision
    }

    /**
     * How many of the readings Health Connect has granted, or null where
     * it could not be asked.
     *
     * The null is the point of this existing rather than
     * [HealthConnect.granted] being called directly: that one flattens a
     * failure into an empty set, which here would be indistinguishable
     * from a phone that has genuinely granted nothing.
     *
     * Health Connect missing altogether is a real zero and not a failure.
     * It cannot start answering without an install, and an install is a
     * thing the person does deliberately -- at which point the switch is
     * where they say so.
     */
    private suspend fun grantedReads(context: Context): Int? = withContext(Dispatchers.IO) {
        when (HealthConnect.availability(context)) {
            HealthAvailability.NotInstalled -> 0
            HealthAvailability.NeedsUpdate -> null
            HealthAvailability.Ready -> {
                val client = HealthConnect.clientOrNull(context)
                if (client == null) {
                    null
                } else {
                    runCatching { client.permissionController.getGrantedPermissions() }
                        .getOrNull()
                        ?.intersect(HealthConnect.readPermissions)
                        ?.size
                }
            }
        }
    }
}

/**
 * What the first run of this version should write, given what it finds.
 *
 * Null means write nothing at all, which is not the same as writing false:
 * false is an answer and is remembered, null leaves the question open for
 * the next launch to ask again.
 *
 * A stored tile layout outranks the permissions, and deliberately. It is a
 * thing somebody did by hand inside this app, it cannot happen by
 * accident, and it is still true on a phone whose Health Connect will not
 * answer right now -- so it is the one piece of evidence worth acting on
 * without a second opinion.
 *
 * *Any* granted reading counts, not all thirteen. Health Connect hands
 * them out one at a time and a partial grant is the normal state of this
 * app -- the Permissions screen says "eleven of thirteen" out loud -- so
 * requiring the lot would switch the half off under somebody who had been
 * reading eleven of them every morning.
 */
internal fun healthOnDecision(
    settled: Boolean,
    grantedReads: Int?,
    tilesConfigured: Boolean,
): Boolean? = when {
    settled -> null
    tilesConfigured -> true
    grantedReads == null -> null
    else -> grantedReads > 0
}

/**
 * Whether a route belongs to the health half.
 *
 * A predicate rather than a list, because a list is a second place to
 * remember a screen and the one thing certain about this app is that it
 * grows health screens. Every one of them is named `health…` but five,
 * and those five are named below; nothing on the relay side of the app
 * starts with the word.
 *
 * Export is in it. The screen behind that row writes your readings out as
 * files, so with nothing being read it is a page of empty sections.
 */
fun isHealthRoute(route: String?): Boolean {
    val name = route ?: return false
    return name.startsWith("health") || name in OTHER_HEALTH_ROUTES
}

/**
 * The health routes that are not named after the half they belong to.
 *
 * `ROUTE` on both objects is a `const`, so naming them here costs a
 * compile-time inline rather than a reference to a class full of
 * notification code.
 */
private val OTHER_HEALTH_ROUTES: Set<String> = setOf(
    JournalReminder.ROUTE,
    MorningMoment.ROUTE,
    "readiness",
    "fitnessage",
    "export",
)

/**
 * Whether this build refuses to open a route at all, whatever else is
 * switched on.
 *
 * One entry: the journal, which is hidden rather than removed -- see
 * [JournalReminder.hidden] for why and for what is still there. The
 * screen is still in the graph, because taking it out would mean a graph
 * and a list of exceptions to keep in step; what cannot happen is anybody
 * arriving at it, and the one way left was a notification posted by a
 * build that still offered it.
 */
fun isHiddenRoute(route: String?): Boolean = route == JournalReminder.ROUTE
