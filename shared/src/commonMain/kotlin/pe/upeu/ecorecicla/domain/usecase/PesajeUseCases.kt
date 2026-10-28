package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.repository.PesajeRepository

/**
 * Validacion del formulario que funciona SIN conexion. Usa los mismos nombres de
 * campo que el servicio, asi el mensaje local y el del 400 se muestran igual.
 * La regla autoritativa (RN-03) sigue estando en el servicio.
 */
fun validarPesaje(datos: DatosPesaje): Map<String, String> = buildMap {
    if (datos.material.isBlank()) put("material", "Indique el tipo de material")
    val bruto = datos.pesoBruto
    val tara = datos.tara
    when {
        bruto == null -> put("pesoBruto", "Ingrese el peso bruto")
        bruto <= 0 -> put("pesoBruto", "El peso bruto debe ser mayor que cero")
    }
    when {
        tara == null -> put("tara", "Ingrese la tara")
        tara < 0 -> put("tara", "La tara no puede ser negativa")
        bruto != null && bruto > 0 && bruto - tara <= 0 -> put("tara", "La tara debe ser menor que el peso bruto")
    }
    // SC-C: opcional, pero si se indica debe ser un porcentaje valido
    datos.humedadEstimada?.let { h ->
        if (h < 0 || h > 100) put("humedadEstimada", "La humedad debe estar entre 0 y 100 %")
    }
}

class RegistrarPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(datos: DatosPesaje): Result<Pesaje> {
        val errores = validarPesaje(datos)
        if (errores.isNotEmpty()) return Result.failure(ErrorApiException(ErrorApi.Validacion(errores)))
        return pesajes.registrar(datos)
    }
}

class CorregirPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(id: Long, datos: DatosPesaje): Result<Pesaje> {
        val errores = validarPesaje(datos)
        if (errores.isNotEmpty()) return Result.failure(ErrorApiException(ErrorApi.Validacion(errores)))
        return pesajes.corregir(id, datos)
    }
}

class AnularPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(id: Long, motivo: String): Result<Unit> {
        if (motivo.isBlank()) {
            return Result.failure(ErrorApiException(ErrorApi.Validacion(mapOf("motivo" to "Indique el motivo de la anulacion"))))
        }
        return pesajes.anular(id, motivo.trim())
    }
}
