package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.LectorDeToken
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.Reloj
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.data.local.enDisco
import pe.upeu.ecorecicla.data.remote.AvisosDeSesion
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.dto.aSesion
import pe.upeu.ecorecicla.data.remote.ejecutarLlamada
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val api: EcoApi,
    private val almacen: AlmacenSeguro,
    private val avisos: AvisosDeSesion,
    private val rutaLocal: RutaLocal,
    private val pesajeLocal: PesajeLocal,
    private val reloj: Reloj
) : AuthRepository {

    override val sesionTerminada: Flow<ErrorApi> = avisos.sesionTerminada

    override suspend fun iniciarSesion(usuario: String, clave: String): Result<Sesion> =
        ejecutarLlamada { api.login(LoginRequestDto(usuario, clave)) }.map { respuesta ->
            // Si otro usuario dejo datos en el equipo compartido, no se mezclan con los del nuevo turno.
            val anterior = enDisco { almacen.leerToken() }?.let { LectorDeToken.leer(it)?.sesion?.usuario }
            if (anterior != null && anterior != respuesta.username) limpiarDatosLocales()
            enDisco { almacen.guardarToken(respuesta.token) }
            respuesta.aSesion()
        }

    override suspend fun sesionGuardada(): Sesion? {
        val token = enDisco { almacen.leerToken() } ?: return null
        val sesion = LectorDeToken.sesionVigente(token, reloj.ahoraMs())
        // Token vencido: se borra y la aplicacion vuelve al inicio de sesion.
        if (sesion == null) enDisco { almacen.borrarToken() }
        return sesion
    }

    override suspend fun perfil(): Result<Sesion> {
        val token = enDisco { almacen.leerToken() }
            ?: return Result.failure(ErrorApiException(ErrorApi.NoAutenticado))
        return ejecutarLlamada { api.usuarioActual() }.map { it.aSesion(token) }
    }

    /** Borra el token del almacen seguro y la copia local: el equipo queda limpio. */
    override suspend fun cerrarSesion() {
        enDisco { almacen.borrarToken() }
        limpiarDatosLocales()
    }

    private suspend fun limpiarDatosLocales() {
        pesajeLocal.borrarTodo()
        rutaLocal.borrarTodo()
    }
}
