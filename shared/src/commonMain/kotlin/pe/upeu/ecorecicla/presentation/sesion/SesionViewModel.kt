package pe.upeu.ecorecicla.presentation.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.model.comoErrorApi
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarFinDeSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObtenerSesionUseCase
import pe.upeu.ecorecicla.presentation.components.mensaje

sealed interface EstadoSesion {
    data object Comprobando : EstadoSesion
    data class SinSesion(val aviso: String? = null) : EstadoSesion
    data class Activa(val sesion: Sesion) : EstadoSesion
}

data class LoginUiState(
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,
    val errorUsuario: String? = null,
    val errorClave: String? = null,
    val mensaje: String? = null
)

/**
 * Decide si se muestra el inicio de sesion o el area protegida.
 *  - Al abrir: si el token guardado sigue vigente se entra directo (RF-A02).
 *  - Si cualquier peticion recibe 401, el cliente borra el token y aqui se vuelve al login.
 */
class SesionViewModel(
    private val iniciarSesion: IniciarSesionUseCase,
    private val obtenerSesion: ObtenerSesionUseCase,
    observarFinDeSesion: ObservarFinDeSesionUseCase
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoSesion>(EstadoSesion.Comprobando)
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    init {
        viewModelScope.launch {
            _estado.value = obtenerSesion()?.let { EstadoSesion.Activa(it) } ?: EstadoSesion.SinSesion()
        }
        viewModelScope.launch {
            observarFinDeSesion().collect {
                if (_estado.value is EstadoSesion.Activa) {
                    _estado.value = EstadoSesion.SinSesion("Tu sesion vencio o el token ya no es valido. Inicia sesion nuevamente.")
                }
            }
        }
    }

    fun cambiarUsuario(valor: String) = _login.update { it.copy(usuario = valor, errorUsuario = null, mensaje = null) }

    fun cambiarClave(valor: String) = _login.update { it.copy(clave = valor, errorClave = null, mensaje = null) }

    fun ingresar() {
        val actual = _login.value
        if (actual.cargando) return
        _login.update { it.copy(cargando = true, mensaje = null, errorUsuario = null, errorClave = null) }
        viewModelScope.launch {
            iniciarSesion(actual.usuario, actual.clave)
                .onSuccess { sesion ->
                    _login.value = LoginUiState()
                    _estado.value = EstadoSesion.Activa(sesion)
                }
                .onFailure { e ->
                    val error = e.comoErrorApi()
                    _login.update {
                        when (error) {
                            is ErrorApi.Validacion -> it.copy(
                                cargando = false,
                                errorUsuario = error.campos["username"],
                                errorClave = error.campos["password"],
                                mensaje = error.campos["general"]
                            )
                            // En el login, 401 = credenciales incorrectas (no sesion vencida)
                            ErrorApi.NoAutenticado -> it.copy(cargando = false, mensaje = "Usuario o contrasena incorrectos")
                            ErrorApi.SinConexion, ErrorApi.TiempoAgotado -> it.copy(
                                cargando = false,
                                mensaje = "No hay conexion con el servidor. Verifica tu red e intentalo de nuevo."
                            )
                            else -> it.copy(cargando = false, mensaje = error.mensaje())
                        }
                    }
                }
        }
    }

    /** Lo llama el perfil despues de cerrar sesion. */
    fun sesionCerrada() {
        _estado.value = EstadoSesion.SinSesion()
    }
}
