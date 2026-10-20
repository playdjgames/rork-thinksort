package com.rork.thinksortandroid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rork.thinksortandroid.data.AppearanceMode

/** ThinkSort design tokens. Fresh, airy, calm — never clinical. */
@Immutable
data class ThinkColors(
    val canvas: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val coral: Color,
    val coralSoft: Color,
    val hairline: Color,
    val isDark: Boolean,
)

val LightThink = ThinkColors(
    canvas = Color(0xFFF4F8F7),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEAF3F0),
    ink = Color(0xFF1F2A28),
    inkSecondary = Color(0xFF5B6E6A),
    inkTertiary = Color(0xFF8CA09B),
    accent = Color(0xFF2E9E8B),
    accentSoft = Color(0xFFE2F1ED),
    coral = Color(0xFFD9674A),
    coralSoft = Color(0xFFFBEBE6),
    hairline = Color(0xFFE3ECE9),
    isDark = false,
)

val DarkThink = ThinkColors(
    canvas = Color(0xFF101614),
    surface = Color(0xFF1A2220),
    surfaceMuted = Color(0xFF202B28),
    ink = Color(0xFFECF2F0),
    inkSecondary = Color(0xFF9BAFAA),
    inkTertiary = Color(0xFF6F837E),
    accent = Color(0xFF45C0A9),
    accentSoft = Color(0xFF1F3A34),
    coral = Color(0xFFF0997F),
    coralSoft = Color(0xFF3A241E),
    hairline = Color(0xFF2A3733),
    isDark = true,
)

val LocalThinkColors = staticCompositionLocalOf { LightThink }

object Think {
    val colors: ThinkColors
        @Composable get() = LocalThinkColors.current
    val cardRadius = 22.dp
    val gutter = 20.dp
}

private val ThinkTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun AppTheme(
    appearance: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val tokens = if (dark) DarkThink else LightThink
    val scheme = if (dark) {
        darkColorScheme(
            primary = tokens.accent, onPrimary = Color.White, background = tokens.canvas, onBackground = tokens.ink,
            surface = tokens.surface, onSurface = tokens.ink, surfaceVariant = tokens.surfaceMuted, onSurfaceVariant = tokens.inkSecondary,
            outline = tokens.hairline, outlineVariant = tokens.hairline, error = tokens.coral, secondaryContainer = tokens.accentSoft,
            onSecondaryContainer = tokens.accent, surfaceContainerHigh = tokens.surface, surfaceContainer = tokens.surface,
            surfaceContainerLow = tokens.canvas,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent, onPrimary = Color.White, background = tokens.canvas, onBackground = tokens.ink,
            surface = tokens.surface, onSurface = tokens.ink, surfaceVariant = tokens.surfaceMuted, onSurfaceVariant = tokens.inkSecondary,
            outline = tokens.hairline, outlineVariant = tokens.hairline, error = tokens.coral, secondaryContainer = tokens.accentSoft,
            onSecondaryContainer = tokens.accent, surfaceContainerHigh = tokens.surface, surfaceContainer = tokens.surface,
            surfaceContainerLow = tokens.canvas,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalThinkColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = ThinkTypography, content = content)
    }
}
