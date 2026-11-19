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

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = GoldAntique,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF422F04),
    tertiary = TealSacred,
    onTertiary = Color.White,
    tertiaryContainer = TealSoft,
    onTertiaryContainer = TealSacred,
    background = IvoryBackground,
    onBackground = TextPrimaryLight,
    surface = AlabasterSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = WarmSandVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFC9C4B8),
    error = ErrorRose,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLuminous,
    onPrimary = Color(0xFF003828),
    primaryContainer = EmeraldNightContainer,
    onPrimaryContainer = EmeraldSoft,
    secondary = GoldBright,
    onSecondary = Color(0xFF3B2900),
    secondaryContainer = GoldDarkContainer,
    onSecondaryContainer = GoldSoft,
    tertiary = Color(0xFF7FD5D8),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF144C4F),
    onTertiaryContainer = TealSoft,
    background = NightBackground,
    onBackground = TextPrimaryDark,
    surface = NightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NightSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF334A42),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val NoorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoorAlMushafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NoorTypography,
        shapes = NoorShapes,
        content = content
    )
}
