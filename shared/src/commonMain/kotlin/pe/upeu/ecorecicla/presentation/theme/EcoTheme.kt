package pe.upeu.ecorecicla.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VerdeEco = Color(0xFF2E7D32)
private val VerdeClaro = Color(0xFF81C784)
private val Ambar = Color(0xFFF9A825)

private val Claro = lightColorScheme(
    primary = VerdeEco,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFF00695C),
    tertiary = Ambar,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = Color(0xFF4A3500)
)

private val Oscuro = darkColorScheme(
    primary = VerdeClaro,
    onPrimary = Color(0xFF0B3D0F),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFD54F),
    tertiaryContainer = Color(0xFF5D4300),
    onTertiaryContainer = Color(0xFFFFECB3)
)

@Composable
fun EcoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}
