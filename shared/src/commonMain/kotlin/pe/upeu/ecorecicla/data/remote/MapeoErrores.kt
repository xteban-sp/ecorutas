package pe.upeu.ecorecicla.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import pe.upeu.ecorecicla.data.remote.dto.ErrorDto
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException

/**
 * UNICO punto donde las excepciones de Ktor se traducen a [ErrorApi].
 * Los timeouts heredan de IOException, por eso se capturan antes.
 */
suspend fun <T> ejecutarLlamada(bloque: suspend () -> T): Result<T> =
    try {
        Result.success(bloque())
    } catch (cancelacion: CancellationException) {
        // Nunca se convierte en fallo: la corrutina debe terminar.
        throw cancelacion
    } catch (e: ClientRequestException) {
        Result.failure(ErrorApiException(traducir(e.response)))
    } catch (e: ServerResponseException) {
        Result.failure(ErrorApiException(ErrorApi.Servidor))
    } catch (e: ResponseException) {
        Result.failure(ErrorApiException(ErrorApi.Servidor))
    } catch (e: HttpRequestTimeoutException) {
        Result.failure(ErrorApiException(ErrorApi.TiempoAgotado))
    } catch (e: ContentConvertException) {
        Result.failure(ErrorApiException(ErrorApi.Servidor))
    } catch (e: SerializationException) {
        Result.failure(ErrorApiException(ErrorApi.Servidor))
    } catch (e: IOException) {
        // Sin red, host inalcanzable, servidor apagado
        Result.failure(ErrorApiException(ErrorApi.SinConexion))
    }

/**
 * Traduce un 4xx usando el formato de error comun del servicio. Para el 400 se
 * conservan los mensajes por campo (validationErrors); para el 401 solo importa
 * el codigo, no el cuerpo.
 */
suspend fun traducir(respuesta: HttpResponse): ErrorApi {
    val codigo = respuesta.status.value
    if (codigo == 401) return ErrorApi.NoAutenticado
    val cuerpo = runCatching { respuesta.body<ErrorDto>() }.getOrNull()
    val mensaje = cuerpo?.message
    return when (codigo) {
        400 -> ErrorApi.Validacion(cuerpo?.validationErrors.orEmpty().ifEmpty { mapOf("general" to (mensaje ?: "Datos invalidos")) })
        403 -> ErrorApi.SinPrivilegio(mensaje ?: "No tiene permiso para realizar esta operacion")
        404 -> ErrorApi.NoEncontrado(mensaje ?: "El recurso ya no existe")
        409 -> ErrorApi.Conflicto(mensaje ?: "La operacion no esta permitida")
        else -> ErrorApi.Servidor
    }
}
