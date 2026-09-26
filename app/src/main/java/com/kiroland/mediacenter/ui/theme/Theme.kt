package com.kiroland.mediacenter.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.kiroland.mediacenter.R

// "D2" design: deep blue-grey ground, coral accent, teal second accent, big rounded tiles.
val Accent = Color(0xFFFF8A65)
val OnAccent = Color(0xFF101820)
val Teal = Color(0xFF6FD3F2)
val Violet = Color(0xFFB3A4FF)
val Background = Color(0xFF101820)
val SidebarColor = Color(0xFF16222C)
val SurfaceColor = Color(0xFF1C2B36)
val TextPrimary = Color(0xFFEEF3F7)
val TextSecondary = Color(0xFFC9D5DE)
val TextMuted = Color(0xFF8FA3B3)
val Success = Color(0xFF5BD18B)
val Danger = Color(0xFFFF6B6B)

/** Tints for tiles that are not artwork, so a row of them does not read as one block. */
object TileTints {
    val Violet = Color(0xFF2A2440)
    val Green = Color(0xFF22321F)
    val Blue = Color(0xFF1C3342)
    val Brown = Color(0xFF3A2618)
}

object Shapes {
    val Tile = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(50)
}

/** The focus ring every tile and card shares. */
val FocusBorder = Border(BorderStroke(3.dp, Accent))

/** Every weight from one variable font file (licences: docs/licenses). */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

val DisplayFont: FontFamily = variableFamily(R.font.space_grotesk, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
val BodyFont: FontFamily = variableFamily(
    R.font.manrope, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Teal,
    onSecondary = OnAccent,
    tertiary = Violet,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF243642),
    onSurfaceVariant = TextSecondary,
    secondaryContainer = Color(0xFF34495A),
    onSecondaryContainer = TextPrimary,
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    border = Color(0xFF2B4657),
    error = Danger,
)

private fun display(size: Int, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = DisplayFont, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.15f).sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = BodyFont, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4f).sp)

private val typography = Typography(
    displayLarge = display(48),
    displayMedium = display(40),
    displaySmall = display(34),
    headlineLarge = display(32),
    headlineMedium = display(28),
    headlineSmall = display(24),
    titleLarge = display(20, FontWeight.SemiBold),
    titleMedium = body(16, FontWeight.Bold),
    titleSmall = body(14, FontWeight.Bold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(12),
    labelLarge = body(14, FontWeight.Bold),
    labelMedium = body(12, FontWeight.Bold),
    labelSmall = body(11, FontWeight.ExtraBold),
)

@Composable
fun MediaCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Root surface: sets the background and the default content color for plain Text.
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = colors.background, contentColor = colors.onBackground),
            content = { content() },
        )
    }
}
