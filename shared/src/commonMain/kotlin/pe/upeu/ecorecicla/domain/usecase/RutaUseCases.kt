package pe.upeu.ecorecicla.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

// Todas las lecturas observan la base local: la pantalla nunca lee de la red.

class ObservarRutaUseCase(private val rutas: RutaRepository) {
    operator fun invoke(): Flow<Ruta?> = rutas.observarRuta()
}

/**
 * Puntos en orden de visita. La busqueda y el filtro "solo pendientes de pesar" (SC-A)
 * se resuelven en la consulta local: funcionan igual sin conexion.
 */
class ObservarPuntosUseCase(private val rutas: RutaRepository) {
    operator fun invoke(busqueda: String, soloPendientes: Boolean = false): Flow<List<PuntoAcopio>> =
        rutas.observarPuntos(busqueda.trim(), soloPendientes)
}

class ObservarPuntoUseCase(private val rutas: RutaRepository) {
    operator fun invoke(id: Long): Flow<PuntoAcopio?> = rutas.observarPunto(id)
}

class ObservarPesajesUseCase(private val pesajes: PesajeRepository) {
    operator fun invoke(puntoId: Long): Flow<List<Pesaje>> = pesajes.observarPorPunto(puntoId)
}

class ObservarUltimaSincronizacionUseCase(private val rutas: RutaRepository) {
    operator fun invoke(): Flow<Long?> = rutas.observarUltimaSincronizacion()
}

class ObservarPendientesUseCase(private val pesajes: PesajeRepository) {
    operator fun invoke(): Flow<Long> = pesajes.observarCantidadPendientes()
}

class CerrarRutaUseCase(private val rutas: RutaRepository) {
    suspend operator fun invoke(): Result<Unit> = rutas.cerrarRuta()
}
