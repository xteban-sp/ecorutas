package pe.upeu.ecorecicla.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository

class IniciarSesionUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(usuario: String, clave: String): Result<Sesion> {
        val campos = buildMap {
            if (usuario.isBlank()) put("username", "Ingrese su usuario")
            if (clave.isBlank()) put("password", "Ingrese su contrasena")
        }
        if (campos.isNotEmpty()) return Result.failure(ErrorApiException(ErrorApi.Validacion(campos)))
        return auth.iniciarSesion(usuario.trim(), clave)
    }
}

class ObtenerSesionUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(): Sesion? = auth.sesionGuardada()
}

class ObtenerPerfilUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(): Result<Sesion> = auth.perfil()
}

class CerrarSesionUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.cerrarSesion()
}

class ObservarFinDeSesionUseCase(private val auth: AuthRepository) {
    operator fun invoke(): Flow<ErrorApi> = auth.sesionTerminada
}
