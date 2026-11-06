package com.srineere.airbridge.ui

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras

/**
 * The three controls that do less than they look like they do.
 *
 * Each of these was reported as broken, and none of them is. Sync cannot
 * reach your band, Measure holds it so nothing else can, and Relay is a
 * link rather than a setting. The press does the right thing in every case
 * and the screen simply never said what the right thing was.
 *
 * So each says it once, with a way to never say it again. An explanation
 * that cannot be turned off stops being an explanation by the third time.
 */
enum class Explain(
    val key: String,
    val title: String,
    val body: String,
    /** What the button that goes ahead says. */
    val go: String,
    /**
     * The one thing to do about it, where there is one.
     *
     * A sentence saying why something cannot work is half an answer. Each
     * of these is a path somebody can follow, and each is taken from
     * somewhere this app already knew it -- the heart screen has shown the
     * sharing toggle after a failed measurement for as long as it has had
     * one, and the handover records why it is needed.
     */
    val how: String? = null,
) {
    Sync(
        "sync",
        "Where readings come from",
        "Your band's readings arrive when Google Health syncs them, and this " +
            "app reads what has arrived. It cannot ask your band itself.\n\n" +
            "So this re-reads. If nothing new has synced, nothing changes.",
        "Re-read",
        how = "To pull the band now: open Google Health — it syncs on launch.",
    ),
    MeasureHeart(
        "measure",
        "Reading the band directly",
        "This asks your Air for a live heart rate, which is the one thing a " +
            "sync cannot do.\n\n" +
            "It holds the band while it runs, so Google Health cannot sync " +
            "during it. It stops on its own after a minute.",
        "Measure",
        // The toggle the transport needs. Without it the write succeeds,
        // nothing arrives, and the band looks broken for forty-five
        // seconds -- which is why this is said before rather than after.
        how = "Needs it on: Google Health › Connections › Air › Share heart rate.",
    ),
    Relay(
        "relay",
        "What relaying means",
        "Relaying holds a link to your band so your notifications can reach " +
            "it as they arrive.\n\n" +
            "It runs in the background with the notification Android requires " +
            "for that, and nothing reaches your wrist while it is off.",
        "Start",
        how = "If it will not start: Alerts › the switch names what is still to allow.",
    ),
    ;

    companion object {
        private const val PREFS = "air_health"
        private const val PREFIX = "explained:"

        fun silenced(context: Context, topic: Explain): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREFIX + topic.key, false)

        fun silence(context: Context, topic: Explain) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFIX + topic.key, true).apply()
        }

        /** How many of the three are currently silenced. */
        fun silencedCount(context: Context): Int = entries.count { silenced(context, it) }

        /**
         * Says them all again.
         *
         * "Don't show this again" has to be undoable somewhere, or it is a
         * decision somebody made once, in a hurry, about a sentence they
         * had not finished reading -- and then cannot get back.
         */
        fun sayAgain(context: Context) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            entries.forEach { editor.remove(PREFIX + it.key) }
            editor.apply()
        }
    }
}

/**
 * Says it, offers to stop saying it, and then does the thing.
 *
 * The tick is checked by default, deliberately. Somebody reading this has
 * just learned the one fact it carries; asking them to also notice a
 * checkbox before they can get on is making them pay twice.
 */
@Composable
internal fun ExplainDialog(
    topic: Explain,
    onDismiss: () -> Unit,
    onGo: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var again by remember { mutableStateOf(true) }

    fun close(go: Boolean) {
        if (again) Explain.silence(context, topic)
        onDismiss()
        if (go) onGo()
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(topic.title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(topic.body, style = MaterialTheme.typography.bodyMedium)
                topic.how?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extras.hrv,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Checkbox(checked = again, onCheckedChange = { again = it })
                    Text(
                        "Don't show this again",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                // Said while the box is ticked, which is the one moment
                // somebody needs it: a dismissal nobody can find their way
                // back from is the reason this line exists at all.
                if (again) {
                    Text(
                        "Device › About brings them back.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extras.disabled,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { close(go = true) }) { Text(topic.go) } },
        dismissButton = { TextButton(onClick = { close(go = false) }) { Text("Not now") } },
    )
}

/**
 * Runs [action], explaining it first the first time.
 *
 * Returned as a pair so a screen holds one piece of state rather than
 * three: `ask` is what the button calls, and `dialog` is what it draws.
 */
@Composable
internal fun explained(topic: Explain, action: () -> Unit): Pair<() -> Unit, @Composable () -> Unit> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var asking by remember { mutableStateOf(false) }
    val ask: () -> Unit = {
        if (Explain.silenced(context, topic)) action() else asking = true
    }
    val dialog: @Composable () -> Unit = {
        if (asking) ExplainDialog(topic, onDismiss = { asking = false }, onGo = action)
    }
    return ask to dialog
}
