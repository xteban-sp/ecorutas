package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSubida
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.model.Sesion

// Solo contratos: el dominio no sabe si los datos vienen de Ktor o de SQLDelight.
// Los fallos llegan como Result.failure(ErrorApiException).

interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, clave: String): Result<Sesion>

    /** Sesion del token guardado si sigue vigente; null si no hay o ya vencio. */
    suspend fun sesionGuardada(): Sesion?

    /** Datos y privilegios del usuario del token, preguntados al servicio (/auth/me). */
    suspend fun perfil(): Result<Sesion>

    /** Borra el token y los datos locales: el equipo queda limpio para el siguiente turno. */
    suspend fun cerrarSesion()

    /** Emite cuando el servicio rechaza el token (401) en cualquier peticion. */
    val sesionTerminada: Flow<ErrorApi>
}

interface RutaRepository {
    fun observarRuta(): Flow<Ruta?>
    /** SC-A: [soloPendientes] deja solo los puntos sin pesar; se combina con la busqueda. */
    fun observarPuntos(busqueda: String, soloPendientes: Boolean = false): Flow<List<PuntoAcopio>>
    fun observarPunto(id: Long): Flow<PuntoAcopio?>
    fun observarUltimaSincronizacion(): Flow<Long?>

    /** Baja la ruta del dia y sus pesajes. Devuelve cuantos pesajes se recibieron. */
    suspend fun descargarRutaDelDia(): Result<Int>

    suspend fun cerrarRuta(): Result<Unit>
}

interface PesajeRepository {
    fun observarPorPunto(puntoId: Long): Flow<List<Pesaje>>
    fun observarCantidadPendientes(): Flow<Long>

    /** Con red se envia; sin red queda en local como pendiente con id negativo. */
    suspend fun registrar(datos: DatosPesaje): Result<Pesaje>
    suspend fun corregir(id: Long, datos: DatosPesaje): Result<Pesaje>
    suspend fun anular(id: Long, motivo: String): Result<Unit>

    /** Envia al servicio cada fila pendiente, en el orden en que se registraron. */
    suspend fun subirPendientes(): Result<ResumenSubida>
}

/** Estado de la red del dispositivo. La implementacion es de cada plataforma. */
interface MonitorConexion {
    val conectado: Flow<Boolean>
}
