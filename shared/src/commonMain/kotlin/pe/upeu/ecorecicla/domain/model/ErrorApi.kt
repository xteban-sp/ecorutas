package pe.upeu.ecorecicla.domain.model

/**
 * Jerarquia sellada de errores del dominio. La capa de datos traduce CADA
 * respuesta del servicio a uno de estos casos (data/remote/MapeoErrores.kt) y la
 * interfaz decide una conducta distinta para cada uno: el `when` es exhaustivo.
 */
sealed interface ErrorApi {
    /** 400: mensajes por campo, para mostrarlos bajo el campo que los origino. */
    data class Validacion(val campos: Map<String, String>) : ErrorApi

    /** 401: token ausente, alterado o vencido (o credenciales incorrectas en el login). */
    data object NoAutenticado : ErrorApi

    /** 403: autenticado sin el privilegio requerido. La sesion se mantiene. */
    data class SinPrivilegio(val mensaje: String) : ErrorApi

    /** 404: el recurso no existe. */
    data class NoEncontrado(val mensaje: String) : ErrorApi

    /** 409: regla de negocio incumplida; se muestra el motivo del servidor. */
    data class Conflicto(val mensaje: String) : ErrorApi

    /** 5xx o respuesta que no se pudo interpretar. */
    data object Servidor : ErrorApi

    data object SinConexion : ErrorApi

    data object TiempoAgotado : ErrorApi
}

class ErrorApiException(val error: ErrorApi) : Exception(error.toString())

/** Sin red o sin respuesta: lo escrito se guarda en local como pendiente. */
fun ErrorApi.esFaltaDeRed(): Boolean = this == ErrorApi.SinConexion || this == ErrorApi.TiempoAgotado

/** Extrae el ErrorApi de un Result fallido. */
fun Throwable.comoErrorApi(): ErrorApi = (this as? ErrorApiException)?.error ?: ErrorApi.Servidor
