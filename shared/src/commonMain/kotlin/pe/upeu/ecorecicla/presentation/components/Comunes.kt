package pe.upeu.ecorecicla.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.upeu.ecorecicla.domain.model.ErrorApi
import kotlin.time.Clock

/** Mensaje de una sola vez (snackbar). Si [reintentar] no es null se ofrece el boton. */
data class Aviso(
    val texto: String,
    val reintentar: (() -> Unit)? = null
)

/** Texto para el usuario de cada error tipado. El `when` es exhaustivo. */
fun ErrorApi.mensaje(): String = when (this) {
    is ErrorApi.Validacion -> campos.values.firstOrNull() ?: "Revise los datos ingresados"
    ErrorApi.NoAutenticado -> "Tu sesion termino. Inicia sesion nuevamente."
    is ErrorApi.SinPrivilegio -> "Operacion no autorizada: $mensaje"
    is ErrorApi.NoEncontrado -> mensaje
    is ErrorApi.Conflicto -> mensaje
    ErrorApi.Servidor -> "El servidor tuvo un problema."
    ErrorApi.SinConexion -> "Sin conexion con el servidor."
    ErrorApi.TiempoAgotado -> "El servidor tardo demasiado en responder."
}

@Composable
fun IndicadorCarga(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** "hace 3 min", "hace 2 h"... a partir de un instante en epoch ms. */
fun haceCuanto(momentoMs: Long?): String {
    if (momentoMs == null) return "nunca"
    val minutos = (Clock.System.now().toEpochMilliseconds() - momentoMs) / 60_000
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        minutos < 24 * 60 -> "hace ${minutos / 60} h"
        else -> "hace ${minutos / (24 * 60)} d"
    }
}

/** 12.5 -> "12.50" sin depender de String.format (no existe en commonMain). */
fun kg(valor: Double): String {
    val centesimos = kotlin.math.round(valor * 100).toLong()
    val signo = if (centesimos < 0) "-" else ""
    val abs = kotlin.math.abs(centesimos)
    return "$signo${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
