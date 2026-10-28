package pe.upeu.ecorecicla.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/**
 * PRIMERO sube lo pendiente y DESPUES baja lo actualizado.
 *
 * Si se invirtiera el orden, la bajada reemplazaria la tabla local con lo que
 * tiene el servidor y el pesaje registrado sin red (que el servidor aun no
 * conoce) no estaria en la respuesta: se veria "perdido" hasta la siguiente
 * vuelta, o se descartaria una correccion local. Subiendo primero, la bajada ya
 * trae esos pesajes con su id definitivo.
 *
 * Se registra como single: el Mutex evita que la sincronizacion manual y la
 * automatica (al recuperar la red) se crucen y envien dos veces la misma fila.
 */
class SincronizarUseCase(
    private val pesajes: PesajeRepository,
    private val rutas: RutaRepository
) {
    private val enCurso = Mutex()

    suspend operator fun invoke(): Result<ResumenSincronizacion> = enCurso.withLock {
        val subida = pesajes.subirPendientes().getOrElse { return Result.failure(it) }
        val recibidos = rutas.descargarRutaDelDia().getOrElse { return Result.failure(it) }
        Result.success(ResumenSincronizacion(subida.enviados, recibidos, subida.rechazos))
    }
}
