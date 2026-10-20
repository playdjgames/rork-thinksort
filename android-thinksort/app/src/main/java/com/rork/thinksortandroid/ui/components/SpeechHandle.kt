package com.rork.thinksortandroid.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rork.thinksortandroid.services.SpeechService

/** Speech service bound to a screen, with the microphone permission handled on first use. */
class SpeechHandle(
    val service: SpeechService,
    private val startWithPermission: (String) -> Unit,
) {
    fun start(seed: String) = startWithPermission(seed)
}

@Composable
fun rememberSpeech(): SpeechHandle {
    val context = LocalContext.current
    val service = remember { SpeechService(context) }
    var pendingSeed by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) service.start(pendingSeed) else service.markDenied()
    }
    DisposableEffect(service) {
        onDispose { service.stop() }
    }
    return remember(service) {
        SpeechHandle(service) { seed ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                service.start(seed)
            } else {
                pendingSeed = seed
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
