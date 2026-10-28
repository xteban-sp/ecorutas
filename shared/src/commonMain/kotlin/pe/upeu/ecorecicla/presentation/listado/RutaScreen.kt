package pe.upeu.ecorecicla.presentation.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.presentation.components.EstadoVacio
import pe.upeu.ecorecicla.presentation.components.IndicadorCarga
import pe.upeu.ecorecicla.presentation.components.haceCuanto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutaScreen(
    viewModel: RutaViewModel,
    alAbrirPunto: (Long) -> Unit,
    alAbrirPerfil: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        val resultado = snackbar.showSnackbar(
            message = aviso.texto,
            actionLabel = if (aviso.reintentar != null) "Reintentar" else null,
            duration = if (aviso.reintentar != null) SnackbarDuration.Long else SnackbarDuration.Short
        )
        viewModel.avisoMostrado()
        if (resultado == SnackbarResult.ActionPerformed) aviso.reintentar?.invoke()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ruta del dia")
                        Text(
                            estado.ruta?.let { "${it.fecha} · ${if (it.cerrada) "CERRADA" else "ABIERTA"}" } ?: "Sin ruta descargada",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::sincronizar, enabled = !estado.sincronizando) {
                        // SC-D: distintivo con los pesajes pendientes de sincronizar
                        BadgedBox(
                            badge = {
                                if (estado.pendientesPorSincronizar > 0) {
                                    Badge { Text("${estado.pendientesPorSincronizar}") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sincronizar (${estado.pendientesPorSincronizar} pendientes)"
                            )
                        }
                    }
                    IconButton(onClick = alAbrirPerfil) { Icon(Icons.Default.Person, contentDescription = "Perfil") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val ruta = estado.ruta
            if (estado.puedeCerrarRuta && ruta != null && !ruta.cerrada) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::pedirCierre,
                    icon = { Icon(Icons.Default.Lock, null) },
                    text = { Text(if (estado.cerrando) "Cerrando..." else "Cerrar ruta") }
                )
            }
        }
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            if (estado.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Ultima sincronizacion: ${haceCuanto(estado.ultimaSincronizacion)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (estado.puntos.isNotEmpty()) {
                    Text("${estado.atendidos}/${estado.puntos.size} pesados", style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedTextField(
                value = estado.busqueda,
                onValueChange = viewModel::buscar,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar por razon social") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            // SC-A: el chip solo cambia el estado; el filtrado lo hace la consulta SQL.
            FilterChip(
                selected = estado.soloPendientes,
                onClick = { viewModel.cambiarSoloPendientes(!estado.soloPendientes) },
                label = { Text("Solo pendientes de pesar") },
                leadingIcon = if (estado.soloPendientes) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            when {
                estado.cargando -> IndicadorCarga()
                estado.puntos.isEmpty() && estado.soloPendientes && estado.busqueda.isBlank() ->
                    EstadoVacio(Icons.Default.CheckCircle, "Todo pesado", "No quedan puntos pendientes de pesar en la ruta.")
                estado.puntos.isEmpty() && (estado.busqueda.isNotBlank() || estado.soloPendientes) ->
                    EstadoVacio(Icons.Default.Search, "Sin coincidencias", "Ningun punto coincide con \"${estado.busqueda}\".")
                estado.puntos.isEmpty() ->
                    EstadoVacio(
                        Icons.Outlined.Map, "No hay puntos para mostrar",
                        "No tienes una ruta asignada hoy o aun no se descargo. Pulsa sincronizar cuando tengas senal."
                    )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(estado.puntos, key = { it.id }) { punto ->
                        TarjetaPunto(punto, onClick = { alAbrirPunto(punto.id) })
                    }
                }
            }
        }
    }

    if (estado.confirmarCierre) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarCierre,
            title = { Text("Cerrar la ruta del dia") },
            text = { Text("Una vez cerrada no se podran registrar ni corregir pesajes. El servicio verificara que todos los puntos esten atendidos.") },
            confirmButton = { TextButton(onClick = viewModel::cerrarRuta) { Text("Cerrar ruta") } },
            dismissButton = { TextButton(onClick = viewModel::cancelarCierre) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TarjetaPunto(punto: PuntoAcopio, onClick: () -> Unit) {
    val colores = if (punto.atendido) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colores, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (punto.atendido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${punto.secuencia}",
                        color = if (punto.atendido) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(punto.razonSocial, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${punto.direccion} · ${punto.zona}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Material habitual: ${punto.materialHabitual}", style = MaterialTheme.typography.bodySmall)
            }
            if (punto.atendido) {
                AssistChip(onClick = onClick, label = { Text("Pesado") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) })
            } else {
                AssistChip(onClick = onClick, label = { Text("Por pesar") })
            }
        }
    }
}
