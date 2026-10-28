package pe.upeu.ecorecicla.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun AlPulsarAtras(habilitado: Boolean, alVolver: () -> Unit) {
    BackHandler(enabled = habilitado, onBack = alVolver)
}
