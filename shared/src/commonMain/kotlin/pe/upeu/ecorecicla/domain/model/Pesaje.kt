package pe.upeu.ecorecicla.domain.model

data class Pesaje(
    val id: Long,                     // negativo mientras esta pendiente de envio
    val puntoId: Long,
    val fechaHora: String,            // ISO-8601
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,             // lo calcula el servicio
    val operario: String,
    val estado: EstadoPesaje,
    val pendiente: Boolean,
    // Propiedades agregadas (la seccion 4.5 lo permite)
    val rutaId: Long = 0,
    val observacion: String = ""
) {
    val anulado: Boolean get() = estado is EstadoPesaje.Anulado
}

/** Lo que escribe el usuario en el formulario. El neto NO se envia: lo calcula el servicio. */
data class DatosPesaje(
    val puntoId: Long,
    val material: String,
    val pesoBruto: Double?,
    val tara: Double?,
    val observacion: String = ""
)
