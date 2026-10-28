package pe.upeu.ecorecicla.domain.model

data class Ruta(
    val id: Long,
    val fecha: String,
    val cerrada: Boolean
)

/** Resultado de subir los pendientes. Un rechazo del servidor no detiene a los demas. */
data class ResumenSubida(
    val enviados: Int,
    val rechazos: List<String>
)

/** Resumen que se muestra al terminar de sincronizar. */
data class ResumenSincronizacion(
    val enviados: Int,
    val recibidos: Int,
    val rechazos: List<String>
)
