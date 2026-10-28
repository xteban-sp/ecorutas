package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.LectorDeToken
import pe.upeu.ecorecicla.data.local.Operacion
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.Reloj
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.data.local.enDisco
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.remote.dto.aPeticionCorreccion
import pe.upeu.ecorecicla.data.remote.dto.aPeticionRegistro
import pe.upeu.ecorecicla.data.remote.ejecutarLlamada
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.ResumenSubida
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import pe.upeu.ecorecicla.domain.model.esFaltaDeRed
import pe.upeu.ecorecicla.domain.repository.PesajeRepository

/**
 * Repositorio offline-first de pesajes.
 *  - Leer: siempre de la base local.
 *  - Registrar / corregir: con red se envia y se guarda la fila definitiva; sin
 *    red se guarda en local como PENDIENTE (id negativo si es nueva) y se
 *    enviara al sincronizar. Un rechazo del servidor (400, 403, 409) no se
 *    guarda: vuelve al formulario para mostrar el motivo.
 *  - Anular: requiere al servidor (solo lo hace el supervisor y no se deshace).
 */
class PesajeRepositoryImpl(
    private val api: EcoApi,
    private val local: PesajeLocal,
    private val rutaLocal: RutaLocal,
    private val almacen: AlmacenSeguro,
    private val reloj: Reloj
) : PesajeRepository {

    override fun observarPorPunto(puntoId: Long): Flow<List<Pesaje>> = local.observarPorPunto(puntoId)

    override fun observarCantidadPendientes(): Flow<Long> = local.observarCantidadPendientes()

    override suspend fun registrar(datos: DatosPesaje): Result<Pesaje> {
        val ruta = rutaLocal.rutaActual()
            ?: return fallo(ErrorApi.NoEncontrado("No hay una ruta del dia descargada; sincronice primero"))
        val nuevo = Pesaje(
            id = 0,
            puntoId = datos.puntoId,
            fechaHora = reloj.ahoraIso(),
            material = datos.material.trim().uppercase(),
            pesoBruto = datos.pesoBruto ?: 0.0,
            tara = datos.tara ?: 0.0,
            // Provisional, solo para mostrar mientras esta pendiente: el definitivo lo calcula el servicio.
            pesoNeto = (datos.pesoBruto ?: 0.0) - (datos.tara ?: 0.0),
            operario = usuarioActual(),
            estado = EstadoPesaje.Registrado,
            pendiente = true,
            rutaId = ruta.id,
            observacion = datos.observacion.trim(),
            humedadEstimada = datos.humedadEstimada
        )
        val enviado = ejecutarLlamada { api.registrarPesaje(nuevo.aPeticionRegistro()) }
        enviado.onSuccess { dto ->
            val definitivo = dto.aDominio()
            local.guardarSincronizado(definitivo)
            return Result.success(definitivo)
        }
        val error = enviado.exceptionOrNull()!!.comoErrorApi()
        return if (error.esFaltaDeRed()) Result.success(local.guardarPendiente(nuevo)) else fallo(error)
    }

    override suspend fun corregir(id: Long, datos: DatosPesaje): Result<Pesaje> {
        val actual = local.obtener(id) ?: return fallo(ErrorApi.NoEncontrado("El pesaje ya no existe"))
        val cambiado = actual.copy(
            material = datos.material.trim().uppercase(),
            pesoBruto = datos.pesoBruto ?: 0.0,
            tara = datos.tara ?: 0.0,
            pesoNeto = (datos.pesoBruto ?: 0.0) - (datos.tara ?: 0.0),
            observacion = datos.observacion.trim(),
            humedadEstimada = datos.humedadEstimada
        )
        // Aun no existe en el servidor: se corrige la fila local y sigue pendiente de crear.
        if (id < 0) {
            local.guardarCorreccionPendiente(cambiado)
            return Result.success(cambiado.copy(pendiente = true))
        }
        val enviado = ejecutarLlamada { api.corregirPesaje(id, datos.aPeticionCorreccion()) }
        enviado.onSuccess { dto ->
            val definitivo = dto.aDominio()
            local.guardarSincronizado(definitivo)
            return Result.success(definitivo)
        }
        val error = enviado.exceptionOrNull()!!.comoErrorApi()
        if (!error.esFaltaDeRed()) return fallo(error)
        local.guardarCorreccionPendiente(cambiado)
        return Result.success(cambiado.copy(pendiente = true))
    }

    override suspend fun anular(id: Long, motivo: String): Result<Unit> {
        // Un pendiente nunca llego al servidor: basta con descartarlo del dispositivo.
        if (id < 0) return Result.success(local.borrar(id))
        return ejecutarLlamada { api.anularPesaje(id, motivo) }.map { local.marcarAnulado(id, motivo) }
    }

    /**
     * Envia cada pendiente y confirma uno por uno: si la red se corta a la mitad,
     * lo ya enviado no se repite (no hay duplicados) y el resto sigue pendiente.
     * Un rechazo de negocio se informa y no detiene a los demas.
     */
    override suspend fun subirPendientes(): Result<ResumenSubida> {
        var enviados = 0
        val rechazos = mutableListOf<String>()
        for ((pesaje, operacion) in local.pendientes()) {
            val crear = operacion == Operacion.CREAR
            val resultado = ejecutarLlamada {
                if (crear) api.registrarPesaje(pesaje.aPeticionRegistro())
                else api.corregirPesaje(pesaje.id, pesaje.aPeticionCorreccion())
            }
            resultado.onSuccess { dto ->
                if (crear) local.confirmarEnvio(pesaje.id, dto.aDominio()) else local.guardarSincronizado(dto.aDominio())
                enviados++
            }
            resultado.onFailure { e ->
                val error = e.comoErrorApi()
                // Sin red, sesion vencida o servidor caido: no tiene sentido seguir. Todo queda pendiente.
                if (error.esFaltaDeRed() || error == ErrorApi.NoAutenticado || error == ErrorApi.Servidor) {
                    return Result.failure(e)
                }
                rechazos += "${pesaje.material} ${pesaje.pesoBruto} kg: ${motivo(error)}"
            }
        }
        return Result.success(ResumenSubida(enviados, rechazos))
    }

    private suspend fun usuarioActual(): String =
        enDisco { almacen.leerToken() }?.let { LectorDeToken.leer(it)?.sesion?.usuario }.orEmpty()

    private fun <T> fallo(error: ErrorApi): Result<T> = Result.failure(ErrorApiException(error))

    private fun motivo(error: ErrorApi): String = when (error) {
        is ErrorApi.Validacion -> error.campos.values.joinToString("; ")
        is ErrorApi.Conflicto -> error.mensaje
        is ErrorApi.SinPrivilegio -> error.mensaje
        is ErrorApi.NoEncontrado -> error.mensaje
        else -> "rechazado por el servidor"
    }
}
