package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.Reloj
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.remote.ejecutarLlamada
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/**
 * Offline-first: las lecturas observan SIEMPRE la base local; el servicio solo
 * sirve para mantenerla al dia (descargarRutaDelDia) y para cerrar la ruta.
 */
class RutaRepositoryImpl(
    private val api: EcoApi,
    private val rutaLocal: RutaLocal,
    private val pesajeLocal: PesajeLocal,
    private val reloj: Reloj
) : RutaRepository {

    override fun observarRuta(): Flow<Ruta?> = rutaLocal.observarRuta()
    override fun observarPuntos(busqueda: String, soloPendientes: Boolean): Flow<List<PuntoAcopio>> =
        rutaLocal.observarPuntos(busqueda, soloPendientes)
    override fun observarPunto(id: Long): Flow<PuntoAcopio?> = rutaLocal.observarPunto(id)
    override fun observarUltimaSincronizacion(): Flow<Long?> = rutaLocal.observarUltimaSincronizacion()

    override suspend fun descargarRutaDelDia(): Result<Int> {
        val ruta = ejecutarLlamada { api.rutaDelDia() }.getOrElse { error ->
            // 404: el usuario no tiene ruta hoy (por ejemplo, el administrador). No es un fallo.
            if ((error as? ErrorApiException)?.error is ErrorApi.NoEncontrado) {
                rutaLocal.reemplazar(null, emptyList())
                pesajeLocal.reemplazarSincronizados(emptyList())
                rutaLocal.marcarSincronizado(reloj.ahoraMs())
                return Result.success(0)
            }
            return Result.failure(error)
        }
        val pesajes = ejecutarLlamada { todasLasPaginas(ruta.id) }.getOrElse { return Result.failure(it) }

        pesajeLocal.reemplazarSincronizados(pesajes.map { it.aDominio() })
        rutaLocal.reemplazar(ruta.aDominio(), ruta.puntos.map { it.aDominio() })
        rutaLocal.marcarSincronizado(reloj.ahoraMs())
        return Result.success(pesajes.size)
    }

    override suspend fun cerrarRuta(): Result<Unit> {
        val ruta = rutaLocal.rutaActual()
            ?: return Result.failure(ErrorApiException(ErrorApi.NoEncontrado("No hay una ruta del dia descargada")))
        return ejecutarLlamada { api.cerrarRuta(ruta.id) }.map { cerrada ->
            rutaLocal.reemplazar(cerrada.aDominio(), cerrada.puntos.map { it.aDominio() })
        }
    }

    /** El listado es paginado: se recorre hasta la ultima pagina. */
    private suspend fun todasLasPaginas(rutaId: Long): List<PesajeDto> {
        val todos = mutableListOf<PesajeDto>()
        var pagina = 0
        do {
            val respuesta = api.pesajesDeRuta(rutaId, pagina)
            todos += respuesta.contenido
            pagina++
        } while (!respuesta.ultima)
        return todos
    }
}
