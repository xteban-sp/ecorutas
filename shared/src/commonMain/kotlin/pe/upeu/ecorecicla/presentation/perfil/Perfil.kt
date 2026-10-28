package pe.upeu.ecorecicla.presentation.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import pe.upeu.ecorecicla.domain.model.esFaltaDeRed
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPendientesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObtenerPerfilUseCase
import pe.upeu.ecorecicla.presentation.components.mensaje

data class PerfilUiState(
    val sesion: Sesion,
    val actualizando: Boolean = true,
    val nota: String? = null,
    val pendientes: Long = 0,
    val confirmarCierre: Boolean = false,
    val cerrada: Boolean = false
)

class PerfilViewModel(
    sesion: Sesion,
    private val obtenerPerfil: ObtenerPerfilUseCase,
    private val cerrarSesionUseCase: CerrarSesionUseCase,
    observarPendientes: ObservarPendientesUseCase
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState(sesion))
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch { observarPendientes().collect { n -> _estado.update { it.copy(pendientes = n) } } }
        viewModelScope.launch {
            // RF-B07: datos y privilegios vigentes segun el servicio
            obtenerPerfil()
                .onSuccess { s -> _estado.update { it.copy(sesion = s, actualizando = false) } }
                .onFailure { e ->
                    val error = e.comoErrorApi()
                    val nota = when {
                        error.esFaltaDeRed() -> "Sin conexion: se muestran los datos guardados en el token"
                        error == ErrorApi.NoAutenticado -> null
                        else -> error.mensaje()
                    }
                    _estado.update { it.copy(actualizando = false, nota = nota) }
                }
        }
    }

    fun cerrarSesion(confirmado: Boolean = false) {
        if (_estado.value.pendientes > 0 && !confirmado) {
            _estado.update { it.copy(confirmarCierre = true) }
            return
        }
        viewModelScope.launch {
            cerrarSesionUseCase()
            _estado.update { it.copy(confirmarCierre = false, cerrada = true) }
        }
    }

    fun cancelarCierre() = _estado.update { it.copy(confirmarCierre = false) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PerfilScreen(viewModel: PerfilViewModel, alVolver: () -> Unit, alCerrarSesion: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.cerrada) { if (estado.cerrada) alCerrarSesion() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }
            )
        }
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Text(estado.sesion.nombre.ifBlank { estado.sesion.usuario }, style = MaterialTheme.typography.titleLarge)
            Text("Usuario: ${estado.sesion.usuario}", style = MaterialTheme.typography.bodyMedium)
            Text("Rol: ${estado.sesion.rol}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (estado.actualizando) Text("Consultando el servicio...", style = MaterialTheme.typography.bodySmall)
            estado.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Text("Privilegios", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                estado.sesion.privilegios.sorted().forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }

            if (estado.pendientes > 0) {
                Text(
                    "Hay ${estado.pendientes} pesaje(s) pendientes de sincronizar en este equipo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Button(
                onClick = { viewModel.cerrarSesion() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Text("  Cerrar sesion")
            }
            Text(
                "Cerrar sesion borra el token del almacen seguro y los datos locales: el equipo queda listo para el siguiente turno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (estado.confirmarCierre) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarCierre,
            title = { Text("Hay pesajes sin enviar") },
            text = { Text("Si cierras sesion ahora se perderan ${estado.pendientes} pesaje(s) pendientes. Sincroniza antes si tienes senal.") },
            confirmButton = { TextButton(onClick = { viewModel.cerrarSesion(confirmado = true) }) { Text("Cerrar igual") } },
            dismissButton = { TextButton(onClick = viewModel::cancelarCierre) { Text("Cancelar") } }
        )
    }
}
