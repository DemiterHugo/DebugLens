package io.github.demiterhugo.debuglens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80E0CA),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF134C40),
    onPrimaryContainer = Color(0xFFC0F5E5),
    secondary = Color(0xFFA9D5D4),
    onSecondary = Color(0xFF143435),
    secondaryContainer = Color(0xFF223D40),
    onSecondaryContainer = Color(0xFFBEE8E7),
    tertiary = Color(0xFFFFBE90),
    onTertiary = Color(0xFF472208),
    tertiaryContainer = Color(0xFF482C1D),
    onTertiaryContainer = Color(0xFFFFDCC4),
    background = Color(0xFF0E181C),
    onBackground = Color(0xFFF1F6F5),
    surface = Color(0xFF152328),
    onSurface = Color(0xFFF1F6F5),
    surfaceVariant = Color(0xFF26373B),
    onSurfaceVariant = Color(0xFFBACBCD),
    surfaceContainerLowest = Color(0xFF0A1418),
    surfaceContainerLow = Color(0xFF142126),
    surfaceContainer = Color(0xFF1B2B30),
    surfaceContainerHigh = Color(0xFF23353A),
    surfaceContainerHighest = Color(0xFF2A3D43),
    outline = Color(0xFF829698),
    outlineVariant = Color(0xFF354B51),
    inverseSurface = Color(0xFFF1F6F5),
    inverseOnSurface = Color(0xFF23343A),
    inversePrimary = Color(0xFF006456),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006456),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF3E7),
    onPrimaryContainer = Color(0xFF003F36),
    secondary = Color(0xFF365F61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEEED),
    onSecondaryContainer = Color(0xFF193E40),
    tertiary = Color(0xFF8B4824),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4CF),
    onTertiaryContainer = Color(0xFF623216),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF17292D),
    surface = Color(0xFFFFFEFB),
    onSurface = Color(0xFF17292D),
    surfaceVariant = Color(0xFFE4EBE7),
    onSurfaceVariant = Color(0xFF445A5C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7F3),
    surfaceContainer = Color(0xFFECF0EB),
    surfaceContainerHigh = Color(0xFFE6ECE7),
    surfaceContainerHighest = Color(0xFFDEE8E2),
    outline = Color(0xFF657C7A),
    outlineVariant = Color(0xFFCEDAD3),
    inverseSurface = Color(0xFF243638),
    inverseOnSurface = Color(0xFFEFF7F4),
    inversePrimary = Color(0xFF80E0CA),
)

private val DebugLensShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DebugLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = DebugLensShapes,
        content = content,
    )
}
