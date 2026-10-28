package pe.upeu.ecorecicla.presentation.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.presentation.components.EstadoVacio
import pe.upeu.ecorecicla.presentation.components.IndicadorCarga
import pe.upeu.ecorecicla.presentation.components.kg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePuntoScreen(viewModel: DetalleViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        val resultado = snackbar.showSnackbar(aviso.texto, actionLabel = if (aviso.reintentar != null) "Reintentar" else null)
        viewModel.avisoMostrado()
        if (resultado == SnackbarResult.ActionPerformed) aviso.reintentar?.invoke()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(estado.punto?.razonSocial ?: "Punto de acopio") },
                navigationIcon = {
                    IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // "Registrar pesaje" solo para quien tiene PESAJE_CREAR
            if (viewModel.puedeRegistrar) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::abrirRegistro,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Registrar pesaje") }
                )
            }
        }
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            estado.punto?.let { punto ->
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Parada ${punto.secuencia} · Zona ${punto.zona}", style = MaterialTheme.typography.labelLarge)
                        Text(punto.direccion, style = MaterialTheme.typography.bodyMedium)
                        Text("Material habitual: ${punto.materialHabitual}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (punto.atendido) "Estado: pesado" else "Estado: pendiente de pesar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text("Pesajes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            when {
                estado.cargando -> IndicadorCarga()
                estado.pesajes.isEmpty() ->
                    EstadoVacio(Icons.Outlined.Inventory2, "Sin pesajes", "Todavia no se registro ningun pesaje en este punto.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estado.pesajes, key = { it.id }) { p ->
                        TarjetaPesaje(
                            pesaje = p,
                            puedeCorregir = viewModel.puedeCorregir(p),
                            puedeAnular = viewModel.puedeAnular(p),
                            alCorregir = { viewModel.abrirCorreccion(p) },
                            alAnular = { viewModel.pedirAnulacion(p) }
                        )
                    }
                }
            }
        }
    }

    estado.formulario?.let { FormularioPesajeDialogo(it, viewModel) }
    estado.anulacion?.let { DialogoAnulacion(it, viewModel) }
}

@Composable
private fun TarjetaPesaje(
    pesaje: Pesaje,
    puedeCorregir: Boolean,
    puedeAnular: Boolean,
    alCorregir: () -> Unit,
    alAnular: () -> Unit
) {
    val colores = when {
        pesaje.pendiente -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        pesaje.anulado -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        else -> CardDefaults.cardColors()
    }
    Card(colors = colores, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pesaje.material} · neto ${kg(pesaje.pesoNeto)} kg",
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (pesaje.anulado) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.weight(1f))
                if (pesaje.pendiente) {
                    Icon(Icons.Default.CloudOff, contentDescription = "Pendiente de envio")
                    Spacer(Modifier.width(4.dp))
                    Text("Pendiente", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                "Bruto ${kg(pesaje.pesoBruto)} kg · Tara ${kg(pesaje.tara)} kg",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${pesaje.fechaHora.replace('T', ' ').take(16)} · ${pesaje.operario} · id ${pesaje.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pesaje.observacion.isNotBlank()) Text(pesaje.observacion, style = MaterialTheme.typography.bodySmall)
            (pesaje.estado as? EstadoPesaje.Anulado)?.let {
                Text("ANULADO: ${it.motivo}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            if (puedeCorregir || puedeAnular) {
                Row {
                    Spacer(Modifier.weight(1f))
                    if (puedeCorregir) TextButton(onClick = alCorregir) { Text("Corregir") }
                    // "Anular" solo para quien tiene PESAJE_ANULAR
                    if (puedeAnular) TextButton(onClick = alAnular) { Text("Anular", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun FormularioPesajeDialogo(form: FormularioPesaje, viewModel: DetalleViewModel) {
    AlertDialog(
        onDismissRequest = { if (!form.guardando) viewModel.cerrarFormulario() },
        title = { Text(if (form.idEditando == null) "Registrar pesaje" else "Corregir pesaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                form.errores["general"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                CampoTexto("Tipo de material", form.material, form.errores["material"], form.guardando) {
                    viewModel.editarFormulario { copy(material = it) }
                }
                CampoTexto("Peso bruto (kg)", form.pesoBruto, form.errores["pesoBruto"], form.guardando, numerico = true) {
                    viewModel.editarFormulario { copy(pesoBruto = it) }
                }
                CampoTexto("Tara del contenedor (kg)", form.tara, form.errores["tara"], form.guardando, numerico = true) {
                    viewModel.editarFormulario { copy(tara = it) }
                }
                CampoTexto("Observacion", form.observacion, form.errores["observacion"], form.guardando) {
                    viewModel.editarFormulario { copy(observacion = it) }
                }
                Text(
                    "El peso neto lo calcula el servicio (bruto - tara).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::guardar, enabled = !form.guardando) {
                Text(if (form.guardando) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cerrarFormulario, enabled = !form.guardando) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CampoTexto(
    etiqueta: String,
    valor: String,
    error: String?,
    deshabilitado: Boolean,
    numerico: Boolean = false,
    alCambiar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        // El mensaje del servidor (o de la validacion local) bajo el campo correspondiente
        supportingText = error?.let { { Text(it) } },
        enabled = !deshabilitado,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DialogoAnulacion(anulacion: AnulacionPesaje, viewModel: DetalleViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelarAnulacion,
        title = { Text("Anular pesaje") },
        text = {
            Column {
                Text("El pesaje no se borra: queda marcado como anulado con el motivo indicado.")
                OutlinedTextField(
                    value = anulacion.motivo,
                    onValueChange = viewModel::cambiarMotivo,
                    label = { Text("Motivo") },
                    isError = anulacion.error != null,
                    supportingText = anulacion.error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::anular, enabled = !anulacion.enviando) {
                Text(if (anulacion.enviando) "Anulando..." else "Anular", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelarAnulacion) { Text("Cancelar") } }
    )
}
