package pe.upeu.ecorecicla.data.remote

/**
 * Direccion base centralizada del servicio EcoAPI. La aporta el platformModule:
 *  - Emulador Android: 10.0.2.2 es el localhost de la PC.
 *  - Celular fisico: IP de la PC en la red local (http://192.168.x.x:8080/api/v1/).
 */
data class ApiConfig(
    val urlBase: String,
    val requestTimeoutMs: Long = 15_000,
    val connectTimeoutMs: Long = 8_000,
    /** Registro de cabeceras HTTP solo en depuracion; en produccion, nada. */
    val registroHttp: Boolean = false
)

/** Ruta (relativa a la URL base) del inicio de sesion: la unica que no lleva token. */
internal const val RUTA_LOGIN = "auth/login"
