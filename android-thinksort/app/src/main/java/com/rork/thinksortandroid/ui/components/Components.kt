package com.rork.thinksortandroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.thinksortandroid.ui.theme.Think

/** White surface with the soft card shadow from the design contract. */
@Composable
fun Modifier.card(color: Color = Think.colors.surface, radius: Dp = Think.cardRadius): Modifier {
    val shape = RoundedCornerShape(radius)
    val shadowColor = if (Think.colors.isDark) Color.Transparent else Color.Black.copy(alpha = 0.10f)
    return this
        .shadow(elevation = 8.dp, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
        .background(color, shape)
        .clip(shape)
}

/** Small uppercase tracked label used above focal content ("NEXT UP", "TODAY"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Think.colors.inkSecondary) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Circular tinted well holding an icon. */
@Composable
fun IconWell(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = Think.colors.accent,
    background: Color = Think.colors.accentSoft,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "press")
    return scale
}

/** Filled teal capsule — the single primary action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = Think.colors.accent,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale(interaction))
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(if (enabled) tint else tint.copy(alpha = 0.45f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Outlined capsule on a surface — secondary and tertiary actions. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Think.colors.ink,
    fill: Color = Think.colors.surface,
    compact: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale(interaction))
            .heightIn(min = if (compact) 46.dp else 54.dp)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(1.dp, Think.colors.hairline), CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (compact) 17.dp else 20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = if (enabled) tint else tint.copy(alpha = 0.5f),
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

/** Plain text button in a given tint. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Think.colors.inkSecondary, enabled: Boolean = true) {
    Text(
        text = text,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

enum class QuickAction { START, DONE, LATER, STUCK, RESUME, NOT_NOW }

/** The Start / Done / Later / I'm Stuck row used on Today and the resume screen. */
@Composable
fun QuickActionRow(actions: List<QuickAction>, onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    val primary = actions.filter { it == QuickAction.START || it == QuickAction.RESUME }
    val secondary = actions - primary.toSet()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        primary.forEach { action ->
            PrimaryButton(
                text = if (action == QuickAction.START) "Start" else "Resume",
                icon = Icons.Rounded.PlayArrow,
                onClick = { onAction(action) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            secondary.forEach { action ->
                when (action) {
                    QuickAction.DONE -> SoftButton("Done", { onAction(action) }, Modifier.weight(1f), icon = Icons.Rounded.Check, compact = true)
                    QuickAction.LATER -> SoftButton("Later", { onAction(action) }, Modifier.weight(1f), icon = Icons.Rounded.Schedule, compact = true)
                    QuickAction.NOT_NOW -> SoftButton("Not Now", { onAction(action) }, Modifier.weight(1f), icon = Icons.Rounded.Schedule, compact = true)
                    QuickAction.STUCK -> SoftButton("I'm Stuck", { onAction(action) }, Modifier.weight(1f), icon = Icons.Rounded.ErrorOutline, tint = Think.colors.coral, compact = true)
                    else -> Unit
                }
            }
        }
    }
}

/** Round check control used across lists — text always accompanies state. */
@Composable
fun CheckCircle(isDone: Boolean, modifier: Modifier = Modifier, tint: Color = Think.colors.accent) {
    val border by animateColorAsState(if (isDone) tint else Think.colors.hairline, label = "check")
    val fill by animateColorAsState(if (isDone) tint.copy(alpha = 0.16f) else Color.Transparent, label = "checkFill")
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.6.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isDone) Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Section container with an uppercase eyebrow over a card. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = Think.colors.surface,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Eyebrow(title, Modifier.weight(1f))
                trailing?.invoke(this)
            }
        }
        Column(Modifier.fillMaxWidth().card(tint), content = content)
    }
}

/** Hairline divider inset to match row content. */
@Composable
fun RowDivider(leading: Dp = 18.dp) {
    Box(Modifier.fillMaxWidth().padding(start = leading).height(1.dp).background(Think.colors.hairline))
}

@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = Think.colors.inkTertiary,
        modifier = modifier.size(20.dp),
    )
}

/** Thin timeline row: time on the left, a hairline rule, then the title. */
@Composable
fun TimelineRow(
    timeLabel: String,
    title: String,
    modifier: Modifier = Modifier,
    isOverdue: Boolean = false,
    showChevron: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            timeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isOverdue) Think.colors.coral else Think.colors.inkSecondary,
            modifier = Modifier.width(78.dp),
            maxLines = 1,
        )
        Box(Modifier.width(1.dp).height(26.dp).background(Think.colors.hairline))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
        if (showChevron) Chevron()
    }
}

/** Plain tappable row content wrapper used inside cards. */
@Composable
fun CardRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun StrikeText(text: String, done: Boolean, modifier: Modifier = Modifier, dimmed: Boolean = done) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (dimmed) Think.colors.inkTertiary else Think.colors.ink,
        textDecoration = if (done) TextDecoration.LineThrough else null,
        modifier = modifier,
    )
}

/** Selectable capsule chip used for durations and filters. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Think.colors.accent else Think.colors.surface, label = "chip")
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, Think.colors.hairline, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Think.colors.ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Accent-tinted focal card ("YOUR NEXT STEP", "FIRST THING"). */
@Composable
fun AccentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Think.cardRadius))
            .background(Think.colors.accentSoft)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Simple white info card with a title and supporting line. */
@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier = modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) IconWell(icon, size = 56.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
    }
}

val PillShape: Shape = CircleShape
