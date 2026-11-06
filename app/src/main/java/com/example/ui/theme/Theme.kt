package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = NothingRedContainerDark,
    onPrimaryContainer = NothingWhite,
    secondary = NothingWhite,
    onSecondary = NothingBlack,
    secondaryContainer = NothingDarkCard,
    onSecondaryContainer = NothingWhite,
    tertiary = StatusWaitlistAmber,
    onTertiary = NothingBlack,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingDarkSurface,
    onSurface = NothingWhite,
    surfaceVariant = NothingDarkCard,
    onSurfaceVariant = NothingDarkMuted,
    outline = NothingDarkBorder,
    outlineVariant = NothingDarkBorder,
    error = NothingRed,
    onError = NothingWhite
)

private val NothingLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = NothingRedContainerLight,
    onPrimaryContainer = NothingRed,
    secondary = NothingBlack,
    onSecondary = NothingWhite,
    secondaryContainer = NothingLightCard,
    onSecondaryContainer = NothingBlack,
    tertiary = StatusWaitlistAmber,
    onTertiary = NothingBlack,
    background = NothingLightBackground,
    onBackground = NothingBlack,
    surface = NothingLightCard,
    onSurface = NothingBlack,
    surfaceVariant = NothingLightCard,
    onSurfaceVariant = NothingLightMuted,
    outline = NothingLightBorder,
    outlineVariant = NothingLightBorder,
    error = NothingRed,
    onError = NothingWhite
)

val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Nothing OS Theme.
 * Full-dark theme by default. Android 12+ dynamic color is explicitly NOT used
 * so the strict monochrome + #D71921 Nothing aesthetic is always preserved.
 */
@Composable
fun TravelTrackTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NothingDarkColorScheme else NothingLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NothingShapes,
        content = content
    )
}
