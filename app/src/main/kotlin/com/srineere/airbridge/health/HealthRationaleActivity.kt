package com.srineere.airbridge.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.AppSettings
import com.srineere.airbridge.ui.theme.AirBridgeTheme

/**
 * What Health Connect shows when someone taps the privacy policy link on its
 * own permission sheet. It is the moment a person is deciding whether to hand
 * over a year of their sleep, so it answers that question and nothing else:
 * what is read, what leaves the phone, and what is written back.
 */
class HealthRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContent {
            AirBridgeTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            // Health Connect launches this on its own, so
                            // nothing above it has already inset for the
                            // status bar the way the main Scaffold does.
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Text("How CUFF uses your health data", style = MaterialTheme.typography.headlineSmall)
                        Paragraph(
                            "CUFF reads your steps, sleep, heart rate, heart rate " +
                                "variability, breathing rate, skin temperature, workouts, " +
                                "calories and weight from Health Connect. It uses them to " +
                                "draw the Health tab: your own trends, and where today sits " +
                                "against your own usual range.",
                        )
                        Paragraph(
                            "None of it leaves your phone. CUFF has no account, no " +
                                "server and no analytics. Nothing is uploaded, shared or sold.",
                        )
                        Paragraph(
                            "The one thing it writes back is food you log in the app " +
                                "yourself, saved as a nutrition entry so that Google Health " +
                                "and anything else you use can read it. Delete an entry in " +
                                "CUFF and it is deleted from Health Connect too.",
                        )
                        Paragraph(
                            "CUFF never writes steps, sleep, heart rate or workouts. " +
                                "Your band already records those through Google Health, and " +
                                "a second copy would be counted twice by every app that " +
                                "reads your totals.",
                        )
                        Paragraph(
                            "You can withdraw any of this at any time in Health Connect. " +
                                "The rest of the app — relaying your calls and notifications " +
                                "to the band — keeps working with none of it granted.",
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Paragraph(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp),
)
