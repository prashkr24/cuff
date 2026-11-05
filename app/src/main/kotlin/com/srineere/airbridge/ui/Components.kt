package com.srineere.airbridge.ui

/**
 * **The two muted inks.** `colorScheme.onSurfaceVariant` is secondary
 * content somebody is meant to read: a stat's label, a row's subtext, the
 * name of a thing. `extras.disabled` is furniture and footnotes they are
 * not: an axis number, an explanatory paragraph, a reading that is missing.
 * They were used interchangeably, including for the same role on different
 * screens, which is why this is written down rather than left to taste.
 */

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.delay

/**
 * The time now, re-read on each minute boundary.
 *
 * Anything that reads "2 min ago" has to become "3 min ago" while it is being
 * looked at. Without this it changed only when something unrelated redrew the
 * screen, so a card left open kept saying "just now" an hour later.
 */
@Composable
internal fun nowTicking(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * A screen title, with a back arrow when the screen was pushed over a tab, and
 * room under it for the one fact that says which of two same-named things this
 * screen is about.
 */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    /** The one thing this screen is for, where a screen has one. */
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(
            start = if (onBack == null) 20.dp else 10.dp,
            end = if (action == null) 20.dp else 12.dp,
            top = 20.dp,
            bottom = 4.dp,
        ),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (action != null) action()
    }
}

/**
 * A label and a figure: the stat that sits under a chart.
 *
 * One of these, used everywhere, and that is the whole point. There were
 * four -- this one, two called `Metric` and one called `Key` -- drawing the
 * same object at three label weights, two label colours, two
 * capitalisations and three figure sizes. A row of figures at two sizes
 * reads as a mistake rather than as emphasis, and so does a screen of them.
 *
 * [tint] draws the dot that ties this figure to its line on the chart
 * above. The dot carries the colour and the figure does not: a coloured
 * numeral competes with the chart it is describing, and a row of them in
 * four colours is a row nobody can scan.
 *
 * A null [value] is a reading nothing has written, and says so with a dash
 * in the muted ink rather than with a blank that looks like a bug.
 */
@Composable
fun Stat(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) = Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tint != null) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(tint))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            // Secondary content, which is read. See the note on the two
            // muted inks at the top of this file.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // One size for every stat, everywhere. Sizing each by its own length
    // made a single row show three figures at two sizes, which reads as a
    // mistake rather than as emphasis -- a long value gets more width from
    // its caller instead.
    Text(
        value ?: "\u2014",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (value == null) MaterialTheme.extras.disabled else Color.Unspecified,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp),
    )
}

@Composable
fun SectionHeader(text: String, action: String? = null, onAction: (() -> Unit)? = null) = Row(
    // Centred, not bottom-aligned: a trailing button is a 48dp touch target
    // whose label sits in the middle of it, so aligning the boxes' bottoms
    // left the action floating a line above its own heading.
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(
        start = 20.dp, end = 8.dp,
        top = if (action == null) 22.dp else 12.dp, bottom = 2.dp,
    ),
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    if (action != null && onAction != null) {
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The card at the top of a screen that says whether something is on, and turns
 * it on. The tint is how this app marks something live, so an off state wears
 * the plain surface instead -- and both screens that have one share the
 * padding rather than each guessing at it.
 */
@Composable
fun StatusToggle(title: String, live: Boolean, onToggle: (Boolean) -> Unit) {
    Box(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
        Surface(
            color = if (live) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (live) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                if (live) MaterialTheme.extras.accentOutline
                else MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = live, onCheckedChange = onToggle)
            }
        }
    }
}

/**
 * The one search field in this app. Every list long enough to need searching
 * is long enough to need a way back out of the search, so the cross is part of
 * the field rather than something each screen remembers to add.
 */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) = TextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    placeholder = { Text(placeholder) },
    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
    trailingIcon = {
        // Only once there is something to clear: a cross in an empty field
        // invites a tap that does nothing.
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
            }
        }
    },
    colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    ),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
)

/**
 * The switch that lives in a row, as against the one in a [StatusToggle].
 *
 * Material gives every switch one size, so the switch that turns the whole
 * relay on and the switch beside the twelfth item in a list were drawn at
 * exactly the same weight. A list of them reads as a list of equals, and the
 * card at the top stops looking like the thing that decides.
 *
 * Scaled rather than rebuilt: the modifier changes what is drawn and not what
 * is measured, so the switch still answers to a full 52 x 32dp of finger.
 */
@Composable
fun RowSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = Modifier.scale(ROW_SWITCH),
)

private const val ROW_SWITCH = 0.82f

/**
 * How far a sub-option sits in from the row it belongs to.
 *
 * A setting that only exists while another is on is a child of it, and the
 * indent is what says so. Two values, not a free number: [UnderIcon] lines a
 * child up with the title of a parent that carries an icon, [UnderTitle] is
 * one step in from a parent that does not, and a third screen cannot invent
 * a third indent.
 */
object Indent {
    /** 20dp gutter + a 38dp icon + the 14dp gap after it. */
    val UnderIcon = 72.dp

    /** 20dp gutter + one step. */
    val UnderTitle = 40.dp
}

/**
 * A tappable row. Presses settle with a spring rather than snapping, which is
 * the motion the whole app uses.
 *
 * [indent] makes it a sub-option of the row above: set in, set smaller, and
 * without an icon of its own, because a child that is drawn exactly like its
 * parent is not read as a child.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    indent: Dp = 0.dp,
) {
    val nested = indent > 0.dp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rowPress",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
            )
            .heightIn(min = if (nested) 48.dp else 56.dp)
            .padding(
                start = if (nested) indent else 20.dp,
                end = 20.dp,
                top = if (nested) 6.dp else 10.dp,
                bottom = if (nested) 6.dp else 10.dp,
            ),
    ) {
        if (leading != null) leading()
        else if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (nested) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = if (nested) MaterialTheme.typography.bodySmall
                        else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
