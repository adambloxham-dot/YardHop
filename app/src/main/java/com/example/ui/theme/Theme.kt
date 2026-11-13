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

private val YardHopLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = MarigoldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = MarigoldContainer,
    onTertiaryContainer = OnMarigoldContainer,
    background = CreamBackground,
    onBackground = EspressoText,
    surface = AlabasterSurface,
    onSurface = EspressoText,
    surfaceVariant = LinenSurfaceVariant,
    onSurfaceVariant = WarmSlateText,
    outline = WarmOutline
)

private val YardHopDarkColorScheme = darkColorScheme(
    primary = DarkCoralPrimary,
    onPrimary = Color(0xFF3E0B02),
    primaryContainer = Color(0xFF6E2110),
    onPrimaryContainer = TerracottaContainer,
    secondary = DarkSageSecondary,
    onSecondary = Color(0xFF072B20),
    secondaryContainer = Color(0xFF1D4D3E),
    onSecondaryContainer = SageContainer,
    tertiary = DarkMarigoldTertiary,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF5C3800),
    onTertiaryContainer = MarigoldContainer,
    background = DarkEspressoBackground,
    onBackground = DarkSandText,
    surface = DarkSurface,
    onSurface = DarkSandText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedSand,
    outline = DarkOutline
)

val YardHopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun YardHopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YardHopDarkColorScheme else YardHopLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YardHopTypography,
        shapes = YardHopShapes,
        content = content
    )
}
