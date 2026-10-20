package com.rork.thinksortandroid.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.services.PurchaseService
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.ThinkSheet
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.theme.Think

private const val PRIVACY_URL = "https://playdjgames.com/privacy"
private const val TERMS_URL = "https://playdjgames.com/support"

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private val benefits = listOf(
    Triple(Icons.Rounded.Mic, "Dump your brain", "Ramble in one box and get back sorted tasks, prep, lists and ideas."),
    Triple(Icons.Rounded.ArrowCircleRight, "One next step", "ThinkSort always answers “what do I do now?” — and why it matters."),
    Triple(Icons.Rounded.Bookmark, "Never lose your place", "Pick up exactly where you stopped, even days later."),
    Triple(Icons.Rounded.TrackChanges, "Focus & unstick", "Timed sessions, and a tiny first step whenever you freeze."),
    Triple(Icons.Rounded.Shield, "Private by design", "Everything stays on your phone. No account, no cloud, no ads."),
)

@Composable
private fun appearScale(): Pair<Float, Float> {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(if (appeared) 1f else 0.85f, spring(dampingRatio = 0.6f), label = "appear")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, label = "appearAlpha")
    return scale to alpha
}

@Composable
private fun PurchaseErrorDialog() {
    val error by PurchaseService.error.collectAsStateWithLifecycle()
    error?.let { message ->
        AlertDialog(
            onDismissRequest = PurchaseService::clearError,
            containerColor = Think.colors.surface,
            title = { Text("Something went wrong", color = Think.colors.ink) },
            text = { Text(message, color = Think.colors.inkSecondary) },
            confirmButton = { TextButton(onClick = PurchaseService::clearError) { Text("OK", color = Think.colors.accent) } },
        )
    }
}

/** The single unlock content. Hard gate after the trial; dismissible sheet from Settings. */
@Composable
private fun ColumnScope.PaywallContent(isHardGate: Boolean) {
    val context = LocalContext.current
    val price by PurchaseService.priceText.collectAsStateWithLifecycle()
    val purchasing by PurchaseService.isPurchasing.collectAsStateWithLifecycle()
    val restoring by PurchaseService.isRestoring.collectAsStateWithLifecycle()
    val (scale, alpha) = appearScale()
    LaunchedEffect(Unit) { if (price == null) PurchaseService.loadProducts() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconWell(Icons.Rounded.Verified, size = 84.dp, modifier = Modifier.scale(scale).alpha(alpha))
        Eyebrow(if (isHardGate) "Your free trial has ended" else "One payment, yours forever")
        Text(if (isHardGate) "Keep your external brain" else "ThinkSort Lifetime", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
        Text(
            if (isHardGate) "Your tasks and notes are safe on this device. Unlock once to get straight back to them."
            else "Unlock everything with a single payment. No subscription, no renewals.",
            style = MaterialTheme.typography.titleLarge,
            color = Think.colors.inkSecondary,
        )
    }

    Column(Modifier.fillMaxWidth().card()) {
        benefits.forEachIndexed { index, (icon, title, body) ->
            BenefitRow(icon, title, body)
            if (index < benefits.size - 1) RowDivider()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryButton(
            price?.let { "Unlock Forever · $it" } ?: "Unlock Forever",
            onClick = { context.findActivity()?.let { PurchaseService.purchase(it) } },
            loading = purchasing,
            enabled = !restoring,
        )
        Text("One-time payment · Lifetime access · No subscription", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary, textAlign = TextAlign.Center)
        SoftButton("Restore Purchase", PurchaseService::restore, compact = true, loading = restoring, enabled = !purchasing)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Already bought ThinkSort on another phone with this Google account? Tap Restore Purchase.",
            style = MaterialTheme.typography.bodySmall,
            color = Think.colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextLink("Privacy Policy", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }, color = Think.colors.accent)
            TextLink("Support", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) }, color = Think.colors.accent)
        }
    }
    PurchaseErrorDialog()
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconWell(icon, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
        }
    }
}

/** Root-level hard gate shown when the trial has ended. Cannot be dismissed. */
@Composable
fun PaywallGateScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Think.colors.canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = Think.gutter, end = Think.gutter, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        PaywallContent(isHardGate = true)
    }
}

/** Dismissible unlock sheet opened from Settings during the trial. */
@Composable
fun PaywallSheet(onDismiss: () -> Unit) {
    val unlocked by PurchaseService.isUnlocked.collectAsStateWithLifecycle()
    LaunchedEffect(unlocked) { if (unlocked) onDismiss() }
    ThinkSheet(title = "ThinkSort Lifetime", onDismiss = onDismiss) {
        PaywallContent(isHardGate = false)
    }
}

private val timeline = listOf(
    Triple("1", "Today", "Full access to everything — brain dump, next step, focus, lists."),
    Triple("3", "Day 3", "We'll remind you the trial is halfway through. No card needed."),
    Triple("7", "Day 7", "Trial ends. Unlock once for lifetime access, or stop — your data stays."),
)

/** Start-your-free-week screen. Activating the trial is the sign-up; no account is created. */
@Composable
fun TrialStartScreen(vm: BrainViewModel, onUnlockNow: () -> Unit) {
    val restoring by PurchaseService.isRestoring.collectAsStateWithLifecycle()
    val (scale, alpha) = appearScale()
    LaunchedEffect(Unit) { PurchaseService.loadProducts() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Think.colors.canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = Think.gutter, end = Think.gutter, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconWell(Icons.Rounded.CardGiftcard, size = 84.dp, modifier = Modifier.scale(scale).alpha(alpha))
            Eyebrow("Free for 7 days")
            Text("Try ThinkSort free for a week", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
            Text(
                "No payment now and no card required. After 7 days it's one payment for lifetime access — never a subscription.",
                style = MaterialTheme.typography.titleLarge,
                color = Think.colors.inkSecondary,
            )
        }
        Column(Modifier.fillMaxWidth().card()) {
            timeline.forEachIndexed { index, (number, title, body) ->
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Think.colors.accentSoft), contentAlignment = Alignment.Center) {
                        Text(number, style = MaterialTheme.typography.titleMedium, color = Think.colors.accent)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                    }
                }
                if (index < timeline.size - 1) RowDivider()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton("Start My Free Week", vm::startTrialIfNeeded, icon = Icons.AutoMirrored.Rounded.ArrowForward)
            TextLink("I'd rather unlock it now", onUnlockNow)
            SoftButton("Restore Purchase", PurchaseService::restore, compact = true, loading = restoring)
        }
    }
    PurchaseErrorDialog()
}
