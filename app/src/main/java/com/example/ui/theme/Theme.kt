package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NearTalkLightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = SoftIceBlue,
    onPrimaryContainer = RoyalBlueDark,
    secondary = RoyalBlueDark,
    onSecondary = Color.White,
    secondaryContainer = SoftIceBlue,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusSuccess,
    onTertiary = Color.White,
    tertiaryContainer = StatusSuccessBg,
    onTertiaryContainer = StatusSuccess,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantBlue,
    onSurfaceVariant = TextSecondary,
    outline = SoftIceBlueBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorBg,
    onErrorContainer = StatusError
)

private val NearTalkDarkColorScheme = darkColorScheme(
    primary = RoyalBlueLight,
    onPrimary = Color(0xFF041E49),
    primaryContainer = Color(0xFF14366E),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = RoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = StatusSuccess,
    onTertiary = Color(0xFF002112),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF2D3F5F),
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF4A1818),
    onErrorContainer = Color(0xFFFFDAD6)
)

val NearTalkShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NearTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NearTalkDarkColorScheme else NearTalkLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NearTalkTypography,
        shapes = NearTalkShapes,
        content = content
    )
}
