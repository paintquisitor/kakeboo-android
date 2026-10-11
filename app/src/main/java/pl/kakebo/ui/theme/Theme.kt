package pl.kakebo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kakebo.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF247D68), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF3E9), onPrimaryContainer = Color(0xFF173E33),
    secondary = Color(0xFF52766B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F0EB), onSecondaryContainer = Color(0xFF314C44),
    tertiary = Color(0xFF8B6A3D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E8D2), onTertiaryContainer = Color(0xFF59411F),
    background = Color(0xFFF7F8F4), onBackground = Color(0xFF1F2A27),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F2A27),
    surfaceVariant = Color(0xFFEDF2EE), onSurfaceVariant = Color(0xFF63736C),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFCFDF9),
    surfaceContainer = Color(0xFFF2F5F1), surfaceContainerHigh = Color(0xFFEBF0EC),
    surfaceContainerHighest = Color(0xFFE3EAE5),
    outline = Color(0xFF8A9992), outlineVariant = Color(0xFFE2E8E2),
    error = Color(0xFFBA514C), onError = Color.White,
    errorContainer = Color(0xFFFCE8E5), onErrorContainer = Color(0xFF702D2A),
    inverseSurface = Color(0xFF29322F), inverseOnSurface = Color(0xFFF0F3EF),
    inversePrimary = Color(0xFF98D8BE), surfaceTint = Color(0xFF247D68),
    scrim = Color(0xFF101713),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF98D8BE), onPrimary = Color(0xFF133A2F),
    primaryContainer = Color(0xFFA8DBC7), onPrimaryContainer = Color(0xFF17382D),
    secondary = Color(0xFFB8CCC4), onSecondary = Color(0xFF233833),
    secondaryContainer = Color(0xFF30413E), onSecondaryContainer = Color(0xFFD7E8E1),
    tertiary = Color(0xFFD3B27C), onTertiary = Color(0xFF433218),
    tertiaryContainer = Color(0xFF51442E), onTertiaryContainer = Color(0xFFF1D7A8),
    background = Color(0xFF141B1D), onBackground = Color(0xFFF0F3EE),
    surface = Color(0xFF202A2D), onSurface = Color(0xFFF0F3EE),
    surfaceVariant = Color(0xFF293538), onSurfaceVariant = Color(0xFFADBDB6),
    surfaceContainerLowest = Color(0xFF101719), surfaceContainerLow = Color(0xFF1B2427),
    surfaceContainer = Color(0xFF202A2D), surfaceContainerHigh = Color(0xFF293538),
    surfaceContainerHighest = Color(0xFF314044),
    outline = Color(0xFF7E918A), outlineVariant = Color(0xFF344145),
    error = Color(0xFFF09C91), onError = Color(0xFF591714),
    errorContainer = Color(0xFF5A2927), onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE3EAE5), inverseOnSurface = Color(0xFF25302D),
    inversePrimary = Color(0xFF247D68), surfaceTint = Color(0xFF98D8BE),
    scrim = Color(0xFF000000),
)
private val KakeboTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun KakeboTheme(mode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KakeboTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
