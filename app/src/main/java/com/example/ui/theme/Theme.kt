package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = AlabasterBackground,
    onBackground = CharcoalOnSurface,
    surface = PureSurface,
    onSurface = CharcoalOnSurface,
    surfaceVariant = SoftMintSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SubtleOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = MintDarkPrimary,
    onPrimary = OnMintDarkPrimary,
    primaryContainer = MintDarkContainer,
    onPrimaryContainer = OnMintDarkContainer,
    secondary = CoralDarkSecondary,
    onSecondary = OnCoralDarkSecondary,
    secondaryContainer = CoralDarkContainer,
    onSecondaryContainer = OnCoralDarkContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = OnAmberContainer,
    onTertiaryContainer = AmberContainer,
    background = PineDarkBackground,
    onBackground = CrispOffWhite,
    surface = PineDarkSurface,
    onSurface = CrispOffWhite,
    surfaceVariant = PineDarkSurfaceVariant,
    onSurfaceVariant = MutedSage,
    outline = DarkOutline
)

val NearbyGoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun NearbyGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = NearbyGoTypography,
        shapes = NearbyGoShapes,
        content = content
    )
}
